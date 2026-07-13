package com.plantinfo.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.plantinfo.data.db.IdentificationEntity
import com.plantinfo.data.repo.toResult
import com.plantinfo.domain.model.edibilitySummaryText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Partage via les mécanismes natifs Android (§2.5) : ACTION_SEND avec un sélecteur système.
 * Les fichiers sont exposés via le FileProvider déclaré au manifeste.
 */
@Singleton
class ShareHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authority get() = "${context.packageName}.fileprovider"

    /** Partage un résumé texte + la première photo de l'identification. */
    fun shareSummary(entity: IdentificationEntity) {
        val result = entity.toResult()
        val text = buildString {
            appendLine(result.commonName)
            appendLine(result.scientificName)
            if (entity.userConfirmed) {
                appendLine("Identification confirmée manuellement")
            } else {
                appendLine("Score : ${result.scoreFinal}/100")
            }
            result.edibilitySummaryText()?.let { appendLine(); appendLine("Comestibilité : $it") }
            result.habitat?.let { appendLine(); appendLine("Habitat : $it") }
            if (entity.latitude != null && entity.longitude != null) {
                appendLine()
                appendLine("Lieu : %.5f, %.5f".format(entity.latitude, entity.longitude))
            }
            appendLine()
            append("— via PlantInfo")
        }

        val photoUri = entity.photoPaths.firstOrNull()?.let { fileUri(File(it)) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (photoUri != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, result.commonName)
        }
        launchChooser(intent, "Partager l'identification")
    }

    /** Partage un fichier PDF déjà généré. */
    fun sharePdf(file: File) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(intent, "Partager la fiche PDF")
    }

    /** Ouvre le PDF dans une visionneuse. */
    fun openPdf(file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri(file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    private fun launchChooser(intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
