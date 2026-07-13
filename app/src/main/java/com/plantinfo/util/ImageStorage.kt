package com.plantinfo.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.plantinfo.domain.model.GpsLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compression et stockage local des photos (§5). Les images sont redimensionnées et compressées
 * en JPEG avant d'être écrites dans le stockage interne de l'app (files/photos), afin de limiter
 * l'espace occupé et le poids des envois réseau vers les API.
 */
@Singleton
class ImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val photosDir: File by lazy {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    /** Décode, redimensionne (côté max ≤ MAX_DIMENSION) et enregistre une photo depuis un Uri. */
    suspend fun saveFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(uri)
        val oriented = applyExifOrientation(uri, bitmap)
        writeJpeg(oriented)
    }

    /** Enregistre une photo déjà décodée (issue de CameraX). */
    suspend fun saveFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        writeJpeg(scaleDown(bitmap))
    }

    /**
     * Extrait les coordonnées GPS inscrites dans l'EXIF d'une photo (§2.2). Pour une photo importée
     * depuis la galerie, ce lieu est celui de la prise de vue réelle — plus pertinent que la position
     * courante de l'appareil. La compression (saveFromUri) supprimant l'EXIF, on lit depuis l'Uri
     * d'origine. Renvoie null si la photo ne contient pas de géotag.
     */
    suspend fun readExifLocation(uri: Uri): GpsLocation? = withContext(Dispatchers.IO) {
        // MediaStore masque les coordonnées des images (confidentialité). Pour lire le géotag, il faut
        // demander l'original non masqué via MediaStore.setRequireOriginal(), ce qui exige :
        //  - la permission ACCESS_MEDIA_LOCATION (+ READ_MEDIA_IMAGES pour qu'elle soit accordable) ;
        //  - un Uri MediaStore. On convertit donc l'Uri « document » (SAF) en Uri MediaStore.
        // On essaie plusieurs candidats et on retient le premier qui expose des coordonnées.
        val candidates = LinkedHashSet<Uri>()
        resolveMediaStoreUri(uri)?.let { candidates.add(it) }
        mediaStoreUriByDisplayName(uri)?.let { candidates.add(it) }
        candidates.add(uri)

        for (candidate in candidates) {
            val original = runCatching { MediaStore.setRequireOriginal(candidate) }.getOrDefault(candidate)
            readLatLongFrom(original)?.let { return@withContext it }
            readLatLongFrom(candidate)?.let { return@withContext it }
        }
        null
    }

    /** Convertit un Uri « document » de la galerie (SAF) en Uri MediaStore, si possible. */
    private fun resolveMediaStoreUri(uri: Uri): Uri? = try {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri) // ex. "image:1000000034"
            val parts = docId.split(":")
            if (parts.size == 2 && parts[0].equals("image", ignoreCase = true)) {
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, parts[1].toLong(),
                )
            } else null
        } else null
    } catch (e: Exception) {
        null
    }

    /** Retrouve l'entrée MediaStore correspondant à l'Uri importé, par nom de fichier (repli). */
    private fun mediaStoreUriByDisplayName(uri: Uri): Uri? = try {
        val name = context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (name.isNullOrBlank()) {
            null
        } else {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use {
                if (it.moveToFirst()) {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.getLong(0))
                } else {
                    null
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun readLatLongFrom(uri: Uri): GpsLocation? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val latLong = exif.latLong ?: return@use null
            val altitude = exif.getAltitude(Double.NaN)
            GpsLocation(
                latitude = latLong[0],
                longitude = latLong[1],
                altitude = if (altitude.isNaN()) null else altitude,
                accuracyMeters = null, // l'EXIF ne fournit pas de précision horizontale
            )
        }
    } catch (e: Exception) {
        null
    }

    /** Lit les octets d'une photo stockée, pour l'envoi aux API. */
    fun readBytes(path: String): ByteArray = File(path).readBytes()

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    private fun writeJpeg(bitmap: Bitmap): String {
        val file = File(photosDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return file.absolutePath
    }

    private fun decodeSampled(uri: Uri): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        }
        val sample = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Impossible de décoder l'image $uri")
        return scaleDown(bitmap)
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) / 2 >= MAX_DIMENSION) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        val w = (bitmap.width * ratio).roundToInt()
        val h = (bitmap.height * ratio).roundToInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 85
    }
}
