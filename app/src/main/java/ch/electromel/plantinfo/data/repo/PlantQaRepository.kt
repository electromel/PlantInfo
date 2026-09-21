package ch.electromel.plantinfo.data.repo

import android.content.Context
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.remote.ai.AiAnswerOutcome
import ch.electromel.plantinfo.data.remote.ai.AiOrchestrator
import ch.electromel.plantinfo.domain.model.edibilitySummaryText
import ch.electromel.plantinfo.util.AppLocales
import ch.electromel.plantinfo.util.StringProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Questions libres posées à l'IA sur une plante déjà identifiée (§Q&A). Construit un prompt qui
 * fournit à l'IA le contexte de la plante (noms, habitat, description, comestibilité) et le lieu de
 * prise de vue, puis délègue le repli entre fournisseurs à l'orchestrateur.
 */
@Singleton
class PlantQaRepository @Inject constructor(
    private val aiOrchestrator: AiOrchestrator,
    private val strings: StringProvider,
    @ApplicationContext private val context: Context,
) {
    suspend fun ask(entity: IdentificationEntity, question: String): AiAnswerOutcome =
        aiOrchestrator.ask(buildPrompt(entity, question))

    private fun buildPrompt(entity: IdentificationEntity, question: String): String {
        val result = entity.toResult()
        return buildString {
            // La réponse suit la langue de l'application, comme la fiche elle-même.
            appendLine(
                "Tu es un expert en botanique et mycologie. Réponds en " +
                    "${AppLocales.current(context).aiName}, de façon claire et concise, à la " +
                    "question de l'utilisateur sur la plante ci-dessous, déjà identifiée.",
            )
            appendLine()
            appendLine("Plante identifiée :")
            appendLine("- Nom commun : ${result.commonName}")
            appendLine("- Nom scientifique : ${result.scientificName}")
            if (result.isFungus) appendLine("- Il s'agit d'un champignon.")
            if (result.isProtected) appendLine("- Espèce potentiellement protégée dans la région.")
            result.habitat?.let { appendLine("- Habitat et répartition : $it") }
            result.description?.let { appendLine("- Description : $it") }
            result.edibilitySummaryText(strings)?.let { appendLine("- Comestibilité : $it") }
            if (entity.latitude != null && entity.longitude != null) {
                append("- Lieu de la prise de vue : %.5f, %.5f".format(entity.latitude, entity.longitude))
                entity.altitude?.let { append(", altitude ${it.toInt()} m") }
                appendLine()
            } else {
                appendLine("- Lieu de la prise de vue : non disponible (photo sans coordonnées GPS).")
            }
            appendLine()
            appendLine("Question de l'utilisateur : \"${question.trim()}\"")
            appendLine()
            appendLine(
                "Utilise le lieu, l'altitude et le climat comme éléments de contexte quand c'est " +
                    "pertinent. Ne donne jamais de conseil de consommation sans rappeler les risques et " +
                    "la nécessité d'une confirmation par un expert (certaines espèces ont des sosies dangereux).",
            )
        }
    }
}
