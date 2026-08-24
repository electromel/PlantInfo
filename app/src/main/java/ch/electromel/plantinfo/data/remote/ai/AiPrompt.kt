package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.domain.model.CareTask
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.SpeciesUse
import ch.electromel.plantinfo.domain.model.UseDomain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Construction du prompt structuré envoyé aux modèles multimodaux et parsing de leur réponse JSON.
 * Le même prompt et le même schéma sont utilisés pour Claude, Gemini et GPT afin de garantir des
 * résultats comparables quel que soit le fournisseur.
 */
object AiPrompt {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Instruction système/utilisateur commune. */
    fun buildInstruction(input: AiAnalysisInput): String {
        val plantNet = if (input.plantNetCandidates.isEmpty()) {
            "Aucun résultat Pl@ntNet fourni (ex. champignon, ou plante non couverte)."
        } else {
            input.plantNetCandidates.joinToString("\n") {
                "- ${it.scientificName} (${it.commonName ?: "nom commun inconnu"}) : score ${it.score}/100"
            }
        }
        val geo = input.gps?.let {
            buildString {
                append("Latitude ${it.latitude}, longitude ${it.longitude}")
                it.altitude?.let { a -> append(", altitude ${a.toInt()} m") }
                it.accuracyMeters?.let { acc -> append(" (précision GPS ~${acc.toInt()} m)") }
            }
        } ?: "Position GPS non disponible."

        return """
Tu es un expert en botanique et mycologie. Analyse la ou les photo(s) fournie(s) d'une plante,
d'un arbre ou d'un champignon et produis une identification.

Contexte :
- Résultats de l'API Pl@ntNet (peu fiable ou absent pour les champignons) :
$plantNet
- Lieu de la prise de vue : $geo
  Utilise la région, l'altitude et le climat comme critères complémentaires de plausibilité.

Consignes :
1. Croise ta propre analyse visuelle avec les résultats Pl@ntNet : valide, corrige ou complète.
2. Pour un champignon, base-toi surtout sur l'image (Pl@ntNet n'est pas fiable ici).
3. Évalue l'état de santé visible (décoloration, taches, flétrissement, parasites) et donne des
   recommandations concrètes si l'état n'est pas satisfaisant.
4. Indique si l'espèce est protégée dans la région détectée (en particulier en Suisse).
5. Renseigne la comestibilité (edible) et la toxicité (toxic) pour l'humain : true/false si tu es sûr,
   null si tu ne peux pas te prononcer. Dans edibilityNote, précise les parties concernées, les
   éventuelles précautions de préparation et surtout les risques et confusions dangereuses.
6. Donne la taille de l'espèce **à maturité** (pas celle du sujet photographié) : matureHeight
   (hauteur) et matureDiameter (diamètre : étalement du houppier ou de la touffe pour une plante,
   diamètre du chapeau pour un champignon), ainsi que timeToMaturity (temps pour atteindre la
   maturité, en années pour une plante ou un arbre, en jours pour un carpophore de champignon).
   Donne une fourchette courte avec son unité (ex. « 15–25 m », « 20–30 ans ») et mets null si tu ne
   peux pas te prononcer — n'invente aucune valeur.
7. Renseigne careCalendar : les opérations saisonnières usuelles pour cette espèce (semis,
   plantation, taille, arrosage, fertilisation, division, protection hivernale, récolte…), chacune
   avec sa période et, si utile, une précision courte. Adapte les périodes à l'hémisphère et au
   climat du lieu de prise de vue. Pour un champignon, décris à la place la période de pousse et de
   cueillette. Liste vide si tu ne peux rien affirmer de fiable.
8. Renseigne uses : les usages documentés de l'espèce, un élément par usage, avec son domaine parmi
   medicinal (santé, phytothérapie), food (alimentation), cosmetic (cosmétique, parfumerie),
   chemical (chimie, teinture, biocides, industrie), craft (bois, fibres, vannerie, matériaux),
   ornamental (ornement, paysage), ecological (mellifère, engrais vert, dépollution, haie), other.
   Reste factuel et historique/traditionnel pour les usages médicinaux : ce n'est jamais un conseil
   thérapeutique. Liste vide si aucun usage notable n'est documenté.
9. Renseigne symbolism : la signification symbolique, culturelle, religieuse ou dans le langage des
   fleurs, si l'espèce en porte une (une à trois phrases, en citant la culture concernée). Mets null
   si l'espèce n'a pas de charge symbolique connue — n'en invente aucune.
10. Donne une confiance globale sur 100 tenant compte de l'accord/désaccord avec Pl@ntNet.
11. Si la confiance est < 60, demande UNE photo complémentaire précise (organ + raison).
12. Fournis 2 à 3 hypothèses alternatives si tu n'es pas certain. Pour CHACUNE, renseigne toxic :
    true si l'espèce est toxique ou vénéneuse pour l'humain, false si elle ne l'est pas, null si tu
    ne peux pas te prononcer. C'est essentiel : l'application avertit l'utilisateur qu'il pourrait
    s'agir d'une espèce toxique lorsqu'une hypothèse toxique reste plausible.

Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour, au format exact suivant :
{
  "commonName": "nom commun en français",
  "scientificName": "Nom latin",
  "confidence": 0-100,
  "isFungus": true|false,
  "isProtected": true|false,
  "alternatives": [{"scientificName":"...","commonName":"...","score":0-100,"toxic":true|false|null}],
  "health": {"status":"description courte","isHealthy":true|false,"recommendations":["..."]},
  "habitat": "habitat et répartition typiques",
  "description": "description et particularités : morphologie, saisonnalité, usages",
  "matureHeight": "hauteur à maturité avec unité, ex. 15–25 m" | null,
  "matureDiameter": "diamètre/étalement à maturité avec unité, ex. 10–15 m" | null,
  "timeToMaturity": "temps pour atteindre la maturité, ex. 20–30 ans" | null,
  "edible": true|false|null,
  "toxic": true|false|null,
  "edibilityNote": "précisions sur comestibilité/toxicité, parties concernées, dangers et confusions",
  "careCalendar": [{"label":"Plantation|Semis|Taille|Arrosage|Fertilisation|Division|Protection hivernale|Récolte|Cueillette","period":"période, ex. mars à avril","note":"précision courte" | null}],
  "uses": [{"domain":"medicinal|food|cosmetic|chemical|craft|ornamental|ecological|other","detail":"usage en une phrase"}],
  "symbolism": "signification symbolique/culturelle et culture concernée" | null,
  "complementaryPhoto": {"organ":"leaf|flower|fruit|bark|habit|cap|gills|other","reason":"..."} | null
}
""".trimIndent()
    }

    /** Parse la réponse texte du modèle en AiAnalysis. Lève AiException(PARSE) si illisible. */
    fun parse(responseText: String): AiAnalysis {
        val jsonText = extractJsonObject(responseText)
            ?: throw AiException(AiFailureReason.PARSE, "Réponse IA sans objet JSON identifiable.")
        val dto = try {
            json.decodeFromString<AiResponseDto>(jsonText)
        } catch (e: Exception) {
            throw AiException(AiFailureReason.PARSE, "JSON IA invalide : ${e.message}", e)
        }
        return dto.toDomain()
    }

    /** Extrait le premier objet JSON équilibré du texte (les modèles ajoutent parfois du texte). */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun mapOrgan(value: String?): PhotoOrgan = when (value?.lowercase()) {
        "leaf" -> PhotoOrgan.LEAF
        "flower" -> PhotoOrgan.FLOWER
        "fruit" -> PhotoOrgan.FRUIT
        "bark" -> PhotoOrgan.BARK
        "habit" -> PhotoOrgan.HABIT
        "cap" -> PhotoOrgan.CAP
        "gills" -> PhotoOrgan.GILLS
        else -> PhotoOrgan.OTHER
    }

    private fun AiResponseDto.toDomain(): AiAnalysis = AiAnalysis(
        commonName = commonName?.takeIf { it.isNotBlank() } ?: scientificName ?: "Inconnu",
        scientificName = scientificName?.takeIf { it.isNotBlank() } ?: "Inconnu",
        confidence = confidence?.coerceIn(0, 100) ?: 0,
        isFungus = isFungus ?: false,
        isProtected = isProtected ?: false,
        alternatives = alternatives.orEmpty().mapNotNull {
            val sci = it.scientificName ?: return@mapNotNull null
            SpeciesCandidate(sci, it.commonName, (it.score ?: 0).coerceIn(0, 100), toxic = it.toxic)
        },
        healthStatus = health?.status ?: "État non évalué",
        isHealthy = health?.isHealthy ?: true,
        recommendations = health?.recommendations.orEmpty().filter { it.isNotBlank() },
        habitat = habitat?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        matureHeight = matureHeight?.takeIf { it.isNotBlank() },
        matureDiameter = matureDiameter?.takeIf { it.isNotBlank() },
        timeToMaturity = timeToMaturity?.takeIf { it.isNotBlank() },
        edible = edible,
        toxic = toxic,
        edibilityNote = edibilityNote?.takeIf { it.isNotBlank() },
        // Une entrée sans libellé ou sans période n'est pas affichable : on l'écarte plutôt que de
        // laisser une ligne vide dans le calendrier.
        careCalendar = careCalendar.orEmpty().mapNotNull {
            val label = it.label?.takeIf { l -> l.isNotBlank() } ?: return@mapNotNull null
            val period = it.period?.takeIf { p -> p.isNotBlank() } ?: return@mapNotNull null
            CareTask(label, period, it.note?.takeIf { n -> n.isNotBlank() })
        },
        uses = uses.orEmpty().mapNotNull {
            val detail = it.detail?.takeIf { d -> d.isNotBlank() } ?: return@mapNotNull null
            SpeciesUse(UseDomain.fromCode(it.domain), detail)
        },
        symbolism = symbolism?.takeIf { it.isNotBlank() },
        complementary = complementaryPhoto?.let {
            val reason = it.reason ?: return@let null
            AiComplementaryRequest(mapOrgan(it.organ), reason)
        },
    )

    // --- DTO de parsing ---

    @Serializable
    private data class AiResponseDto(
        val commonName: String? = null,
        val scientificName: String? = null,
        val confidence: Int? = null,
        val isFungus: Boolean? = null,
        val isProtected: Boolean? = null,
        val alternatives: List<AltDto>? = null,
        val health: HealthDto? = null,
        val habitat: String? = null,
        val description: String? = null,
        val matureHeight: String? = null,
        val matureDiameter: String? = null,
        val timeToMaturity: String? = null,
        val edible: Boolean? = null,
        val toxic: Boolean? = null,
        val edibilityNote: String? = null,
        val careCalendar: List<CareTaskDto>? = null,
        val uses: List<UseDto>? = null,
        val symbolism: String? = null,
        @SerialName("complementaryPhoto") val complementaryPhoto: ComplementaryDto? = null,
    )

    @Serializable
    private data class CareTaskDto(
        val label: String? = null,
        val period: String? = null,
        val note: String? = null,
    )

    // Le domaine reste une String au parsing : un libellé inattendu du modèle doit retomber sur
    // UseDomain.OTHER, pas faire échouer le décodage de toute la réponse.
    @Serializable
    private data class UseDto(
        val domain: String? = null,
        val detail: String? = null,
    )

    @Serializable
    private data class AltDto(
        val scientificName: String? = null,
        val commonName: String? = null,
        val score: Int? = null,
        val toxic: Boolean? = null,
    )

    @Serializable
    private data class HealthDto(
        val status: String? = null,
        val isHealthy: Boolean? = null,
        val recommendations: List<String>? = null,
    )

    @Serializable
    private data class ComplementaryDto(
        val organ: String? = null,
        val reason: String? = null,
    )
}
