package ch.electromel.plantinfo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.prefs.SafetySettingsStore
import ch.electromel.plantinfo.data.repo.toResult
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.UseDomain
import ch.electromel.plantinfo.domain.model.careCalendarSummaryText
import ch.electromel.plantinfo.domain.model.edibilitySummaryText
import ch.electromel.plantinfo.domain.model.iucnStatus
import ch.electromel.plantinfo.domain.model.maturitySummaryText
import ch.electromel.plantinfo.domain.model.toxicConfusionWarningText
import ch.electromel.plantinfo.domain.model.usesSummaryText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Génère un PDF A4 récapitulant une fiche d'identification (§2.5), pour export/partage. Utilise
 * `android.graphics.pdf.PdfDocument` (aucune dépendance externe). Le fichier est écrit dans le
 * cache partagé exposé par le FileProvider.
 *
 * La fiche est paginée : une fiche complète (comestibilité, calendrier, usages, symbolique…) dépasse
 * largement une page, et le texte débordant serait sinon dessiné hors du support, donc perdu.
 */
@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safetySettings: SafetySettingsStore,
    private val strings: AppStrings,
) {
    suspend fun export(entity: IdentificationEntity): File = withContext(Dispatchers.IO) {
        val result = entity.toResult()
        val doc = PdfDocument()

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
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 10f }

        // La date est mise en forme dans la langue de l'application, pas dans celle du téléphone.
        val date = DateFormat
            .getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, Locale.forLanguageTag(strings.language.tag))
            .format(Date(entity.dateTime))
        val writer = PageWriter(
            doc,
            strings.get(R.string.pdf_footer, date),
            strings.get(R.string.pdf_page_number),
            footerPaint,
        )

        // En-tête
        writer.line(
            result.commonName.ifBlank { strings.get(R.string.species_unknown) },
            titlePaint,
            advance = 30f,
            baselineOffset = 18f,
        )
        writer.line(result.scientificName, sciPaint, advance = 18f)
        val scoreText = when {
            entity.userConfirmed -> strings.get(R.string.pdf_confirmed)
            result.aiProvider != AiProviderType.NONE ->
                strings.get(R.string.pdf_score_with_provider, result.scoreFinal, result.aiProvider.label)
            else -> strings.get(R.string.pdf_score, result.scoreFinal)
        }
        writer.line(scoreText, bodyPaint, advance = 24f)

        // Photo (première)
        entity.photoPaths.firstOrNull()?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.let { writer.image(it) }
        }

        if (entity.isFungus) {
            writer.section(
                strings.get(R.string.pdf_fungus_heading), headingPaint,
                strings.get(R.string.pdf_fungus_body), bodyPaint,
            )
        }
        if (result.isProtected) {
            writer.section(
                strings.get(R.string.pdf_protected_heading), headingPaint,
                strings.get(R.string.pdf_protected_body), bodyPaint,
            )
        }
        result.toxicConfusionWarningText(strings, safetySettings.current())?.let {
            writer.section(strings.get(R.string.pdf_toxic_heading), headingPaint, it, bodyPaint)
        }
        result.habitat?.let {
            writer.section(strings.get(R.string.fiche_habitat), headingPaint, it, bodyPaint)
        }
        result.iucnStatus?.let {
            writer.section(
                strings.get(R.string.fiche_conservation_title), headingPaint,
                strings.get(R.string.pdf_conservation_body, strings.get(it.labelRes), it.code), bodyPaint,
            )
        }
        result.health?.let { h ->
            val text = buildString {
                append(h.status.ifBlank { strings.get(R.string.health_not_assessed) })
                if (h.recommendations.isNotEmpty()) {
                    append("\n").append(strings.get(R.string.fiche_health_recommendations))
                    h.recommendations.forEach { append("\n• $it") }
                }
            }
            writer.section(strings.get(R.string.fiche_health), headingPaint, text, bodyPaint)
        }
        result.edibilitySummaryText(strings)?.let {
            writer.section(strings.get(R.string.fiche_edibility_title), headingPaint, it, bodyPaint)
        }
        result.description?.let {
            writer.section(strings.get(R.string.fiche_information), headingPaint, it, bodyPaint)
        }
        result.maturitySummaryText(strings)?.let {
            writer.section(strings.get(R.string.fiche_maturity_title), headingPaint, it, bodyPaint)
        }
        result.careCalendarSummaryText()?.let {
            val heading = strings.get(
                if (result.isFungus) R.string.fiche_calendar_fungus_title else R.string.fiche_calendar_title,
            )
            writer.section(heading, headingPaint, it, bodyPaint)
        }
        result.usesSummaryText(strings)?.let {
            val text = if (result.uses.any { use -> use.domain == UseDomain.MEDICINAL }) {
                it + "\n" + strings.get(R.string.pdf_uses_medicinal_note)
            } else {
                it
            }
            writer.section(strings.get(R.string.fiche_uses_title), headingPaint, text, bodyPaint)
        }
        result.symbolism?.let {
            writer.section(strings.get(R.string.fiche_symbolism_title), headingPaint, it, bodyPaint)
        }

        if (entity.latitude != null && entity.longitude != null) {
            val loc = buildString {
                append("%.5f, %.5f".format(entity.latitude, entity.longitude))
                entity.altitude?.let { append(" • %.0f m".format(it)) }
            }
            writer.section(strings.get(R.string.fiche_location_title), headingPaint, loc, bodyPaint)
        }

        writer.close()

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val safeName = result.scientificName.replace(Regex("[^A-Za-z0-9]+"), "_").take(40)
        val file = File(dir, "PlantInfo_$safeName.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    /**
     * Écrit un flux de texte sur des pages A4 successives : chaque tracé vérifie d'abord qu'il reste
     * la place nécessaire avant le pied de page, et ouvre une nouvelle page sinon. Le pied de page
     * est répété sur chaque page, numérotée à partir de la deuxième.
     */
    private class PageWriter(
        private val doc: PdfDocument,
        private val footerText: String,
        /** Gabarit « pied de page (page N) », déjà traduit : le writer ne connaît pas les ressources. */
        private val pageNumberFormat: String,
        private val footerPaint: Paint,
    ) {
        private var pageNumber = 0
        private var page: PdfDocument.Page = newPage()
        private var y = MARGIN.toFloat()

        private val canvas: Canvas get() = page.canvas

        /** Hauteur utile : au-dessus du pied de page, lui-même à MARGIN du bas. */
        private val bottomLimit get() = (PAGE_H - MARGIN - FOOTER_GAP).toFloat()

        private fun newPage(): PdfDocument.Page {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
            return doc.startPage(info)
        }

        private fun finishPage() {
            val label = if (pageNumber > 1) pageNumberFormat.format(footerText, pageNumber) else footerText
            canvas.drawText(label, MARGIN.toFloat(), (PAGE_H - MARGIN).toFloat(), footerPaint)
            doc.finishPage(page)
        }

        /** Garantit [height] pixels disponibles, en passant à la page suivante si nécessaire. */
        private fun ensureSpace(height: Float) {
            if (y + height <= bottomLimit) return
            finishPage()
            page = newPage()
            y = MARGIN.toFloat()
        }

        /**
         * Écrit une ligne unique (sans retour à la ligne automatique : réservé à l'en-tête, dont les
         * textes sont courts). [baselineOffset] décale la ligne de base sans consommer plus de place.
         */
        fun line(text: String, paint: Paint, advance: Float, baselineOffset: Float = 0f) {
            ensureSpace(advance + baselineOffset)
            canvas.drawText(text, MARGIN.toFloat(), y + baselineOffset, paint)
            y += advance
        }

        /** Insère l'image mise à l'échelle, sur la page suivante si elle n'entre pas ici. */
        fun image(bmp: Bitmap) {
            val maxW = PAGE_W - 2 * MARGIN
            val scale = minOf(maxW.toFloat() / bmp.width, IMAGE_MAX_H.toFloat() / bmp.height)
            val w = (bmp.width * scale).toInt()
            val h = (bmp.height * scale).toInt()
            ensureSpace(h + 16f)
            canvas.drawBitmap(bmp, null, Rect(MARGIN, y.toInt(), MARGIN + w, y.toInt() + h), null)
            y += h + 16
        }

        /** Titre de section + corps avec retour à la ligne, le titre restant collé à sa première ligne. */
        fun section(heading: String, headingPaint: Paint, body: String, bodyPaint: Paint) {
            y += 6
            // Un titre seul en bas de page serait orphelin : on réserve aussi sa première ligne.
            ensureSpace(16f + bodyPaint.textSize + 4f)
            canvas.drawText(heading, MARGIN.toFloat(), y, headingPaint)
            y += 16
            val maxWidth = (PAGE_W - 2 * MARGIN).toFloat()
            body.split("\n").forEach { paragraph -> drawWrapped(paragraph, bodyPaint, maxWidth) }
            y += 8
        }

        private fun drawWrapped(text: String, paint: Paint, maxWidth: Float) {
            val lineHeight = paint.textSize + 4
            val line = StringBuilder()
            for (word in text.split(" ")) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    ensureSpace(lineHeight)
                    canvas.drawText(line.toString(), MARGIN.toFloat(), y, paint)
                    y += lineHeight
                    line.clear().append(word)
                } else {
                    line.clear().append(candidate)
                }
            }
            if (line.isNotEmpty()) {
                ensureSpace(lineHeight)
                canvas.drawText(line.toString(), MARGIN.toFloat(), y, paint)
                y += lineHeight
            }
        }

        /** Clôt la page courante (pied de page compris). À appeler avant d'écrire le document. */
        fun close() = finishPage()
    }

    private companion object {
        const val PAGE_W = 595 // A4 @ 72 dpi
        const val PAGE_H = 842
        const val MARGIN = 40
        const val FOOTER_GAP = 16 // espace réservé au-dessus du pied de page
        const val IMAGE_MAX_H = 260
    }
}
