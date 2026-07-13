package com.plantinfo.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.plantinfo.data.db.IdentificationEntity
import com.plantinfo.data.repo.toResult
import com.plantinfo.domain.model.edibilitySummaryText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Génère un PDF A4 récapitulant une fiche d'identification (§2.5), pour export/partage. Utilise
 * `android.graphics.pdf.PdfDocument` (aucune dépendance externe). Le fichier est écrit dans le
 * cache partagé exposé par le FileProvider.
 */
@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun export(entity: IdentificationEntity): File = withContext(Dispatchers.IO) {
        val result = entity.toResult()
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        var y = MARGIN.toFloat()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x1B, 0x5E, 0x20); textSize = 22f; typeface =
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sciPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x2E, 0x7D, 0x32); textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f }

        // En-tête
        canvas.drawText(result.commonName, MARGIN.toFloat(), y + 18, titlePaint)
        y += 30
        canvas.drawText(result.scientificName, MARGIN.toFloat(), y, sciPaint)
        y += 18
        val scoreText = if (entity.userConfirmed) {
            "Identification confirmée manuellement"
        } else {
            "Score d'exactitude : ${result.scoreFinal}/100" +
                (if (result.aiProvider.name != "NONE") " — analysé par ${result.aiProvider.label}" else "")
        }
        canvas.drawText(scoreText, MARGIN.toFloat(), y, bodyPaint)
        y += 24

        // Photo (première)
        entity.photoPaths.firstOrNull()?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.let { bmp ->
                val maxW = PAGE_W - 2 * MARGIN
                val maxH = 260
                val scale = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
                val w = (bmp.width * scale).toInt()
                val h = (bmp.height * scale).toInt()
                val dest = Rect(MARGIN, y.toInt(), MARGIN + w, y.toInt() + h)
                canvas.drawBitmap(bmp, null, dest, null)
                y += h + 16
            }
        }

        if (entity.isFungus) {
            y = drawSection(canvas, "Avertissement champignon", headingPaint,
                "Ne jamais consommer un champignon sur la seule base de cette identification " +
                    "automatique. En cas de doute, consultez un expert ou un contrôle mycologique.",
                bodyPaint, y)
        }
        if (result.isProtected) {
            y = drawSection(canvas, "Espèce protégée", headingPaint,
                "Espèce potentiellement protégée dans cette région : cueillette déconseillée.",
                bodyPaint, y)
        }
        result.habitat?.let { y = drawSection(canvas, "Habitat et répartition", headingPaint, it, bodyPaint, y) }
        result.health?.let { h ->
            val text = buildString {
                append(h.status)
                if (h.recommendations.isNotEmpty()) {
                    append("\nRecommandations :")
                    h.recommendations.forEach { append("\n• $it") }
                }
            }
            y = drawSection(canvas, "État de santé", headingPaint, text, bodyPaint, y)
        }
        result.edibilitySummaryText()?.let { y = drawSection(canvas, "Comestibilité", headingPaint, it, bodyPaint, y) }
        result.description?.let { y = drawSection(canvas, "Informations", headingPaint, it, bodyPaint, y) }

        if (entity.latitude != null && entity.longitude != null) {
            val loc = buildString {
                append("%.5f, %.5f".format(entity.latitude, entity.longitude))
                entity.altitude?.let { append(" • %.0f m".format(it)) }
            }
            y = drawSection(canvas, "Lieu de la prise de vue", headingPaint, loc, bodyPaint, y)
        }

        val date = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
            .format(Date(entity.dateTime))
        canvas.drawText("Identifié le $date — PlantInfo", MARGIN.toFloat(), (PAGE_H - MARGIN).toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 10f })

        doc.finishPage(page)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safeName = result.scientificName.replace(Regex("[^A-Za-z0-9]+"), "_").take(40)
        val file = File(dir, "PlantInfo_$safeName.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    /** Dessine un titre de section + un corps avec retour à la ligne, renvoie le nouveau y. */
    private fun drawSection(
        canvas: android.graphics.Canvas,
        heading: String,
        headingPaint: Paint,
        body: String,
        bodyPaint: Paint,
        startY: Float,
    ): Float {
        var y = startY + 6
        canvas.drawText(heading, MARGIN.toFloat(), y, headingPaint)
        y += 16
        val maxWidth = (PAGE_W - 2 * MARGIN).toFloat()
        body.split("\n").forEach { paragraph ->
            y = drawWrapped(canvas, paragraph, bodyPaint, y, maxWidth)
        }
        return y + 8
    }

    private fun drawWrapped(
        canvas: android.graphics.Canvas,
        text: String,
        paint: Paint,
        startY: Float,
        maxWidth: Float,
    ): Float {
        var y = startY
        val words = text.split(" ")
        val line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), MARGIN.toFloat(), y, paint)
                y += paint.textSize + 4
                line.clear().append(word)
            } else {
                line.clear().append(candidate)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), MARGIN.toFloat(), y, paint)
            y += paint.textSize + 4
        }
        return y
    }

    private companion object {
        const val PAGE_W = 595 // A4 @ 72 dpi
        const val PAGE_H = 842
        const val MARGIN = 40
    }
}
