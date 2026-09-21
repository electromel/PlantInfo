package ch.electromel.plantinfo.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.prefs.SafetySettingsStore
import ch.electromel.plantinfo.data.repo.toResult
import ch.electromel.plantinfo.domain.model.careCalendarLines
import ch.electromel.plantinfo.domain.model.edibilitySummaryText
import ch.electromel.plantinfo.domain.model.maturitySummaryText
import ch.electromel.plantinfo.domain.model.toxicConfusionWarningText
import ch.electromel.plantinfo.domain.model.usesByDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Partage via les mécanismes natifs Android (§2.5) : ACTION_SEND avec un sélecteur système.
 * Les fichiers sont exposés via le FileProvider déclaré au manifeste.
 */
@Singleton
class ShareHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safetySettings: SafetySettingsStore,
    private val strings: StringProvider,
) {
    private val authority get() = "${context.packageName}.fileprovider"

    /** Partage un résumé texte + la première photo de l'identification. */
    fun shareSummary(entity: IdentificationEntity) {
        val result = entity.toResult()
        val text = buildString {
            appendLine(result.commonName.ifBlank { strings.get(R.string.species_unknown) })
            appendLine(result.scientificName)
            if (entity.userConfirmed) {
                appendLine(strings.get(R.string.share_confirmed))
            } else {
                appendLine(strings.get(R.string.share_score, result.scoreFinal))
            }
            // Avant la comestibilité : un message partagé ne doit pas afficher « Comestible » sans
            // la réserve qui l'accompagne à l'écran.
            result.toxicConfusionWarningText(strings, safetySettings.current())
                ?.let { appendLine(); appendLine("⚠ $it") }
            result.edibilitySummaryText(strings)
                ?.let { appendLine(); appendLine(strings.get(R.string.share_edibility, it)) }
            result.maturitySummaryText(strings)
                ?.let { appendLine(); appendLine(strings.get(R.string.share_maturity, it)) }
            result.habitat?.let { appendLine(); appendLine(strings.get(R.string.share_habitat, it)) }
            // Calendrier et usages sont condensés sur une ligne : un message de partage doit rester
            // lisible d'un coup d'œil, le détail est dans l'app et dans le PDF.
            result.careCalendarLines().takeIf { it.isNotEmpty() }?.let { tasks ->
                appendLine()
                appendLine(
                    strings.get(
                        R.string.share_calendar,
                        tasks.joinToString(" · ") { "${it.label} ${it.period}" },
                    ),
                )
            }
            result.usesByDomain().takeIf { it.isNotEmpty() }?.let { grouped ->
                appendLine()
                appendLine(
                    strings.get(
                        R.string.share_uses,
                        grouped.joinToString(" · ") { (domain, _) -> strings.get(domain.labelRes) },
                    ),
                )
            }
            result.symbolism?.let { appendLine(); appendLine(strings.get(R.string.share_symbolism, it)) }
            if (entity.latitude != null && entity.longitude != null) {
                appendLine()
                appendLine(
                    strings.get(
                        R.string.share_place,
                        "%.5f, %.5f".format(Locale.US, entity.latitude, entity.longitude),
                    ),
                )
            }
            appendLine()
            append(strings.get(R.string.share_signature))
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
            putExtra(Intent.EXTRA_SUBJECT, result.commonName.ifBlank { strings.get(R.string.species_unknown) })
        }
        launchChooser(intent, strings.get(R.string.share_chooser_identification))
    }

    /** Partage un fichier PDF déjà généré. */
    fun sharePdf(file: File) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(intent, strings.get(R.string.share_chooser_pdf))
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
