package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.domain.model.CareTask
import ch.electromel.plantinfo.domain.model.ComplementaryPhotoRequest
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.PropagationMethod
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.SpeciesUse
import ch.electromel.plantinfo.domain.model.UseDomain
import ch.electromel.plantinfo.util.GeoUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Construction du prompt structuré envoyé aux modèles multimodaux et parsing de leur réponse JSON.
 * Le même prompt et le même schéma sont utilisés pour Claude, Gemini et GPT afin de garantir des
 * résultats comparables quel que soit le fournisseur.
 */
object AiPrompt {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Instruction système/utilisateur commune. [today] est la date à laquelle l'utilisateur pourrait
     * prendre les photos complémentaires : sans elle, le modèle demande volontiers des fleurs ou des
     * fruits hors saison.
     */
    fun buildInstruction(input: AiAnalysisInput, today: LocalDate = LocalDate.now()): String {
        val plantNet = if (input.plantNetCandidates.isEmpty()) {
            "Aucun résultat Pl@ntNet fourni (ex. champignon, ou plante non couverte)."
        } else {
            input.plantNetCandidates.joinToString("\n") {
                "- ${it.scientificName} (${it.commonName ?: "nom commun inconnu"}) : score ${it.score}/100"
            }
        }
        // Le lieu est arrondi à ~1 km avant de quitter l'appareil (voir GeoUtils.APPROXIMATE_DECIMALS) :
        // la région, le climat et l'altitude suffisent à juger la plausibilité d'une espèce.
        val geo = input.gps?.let {
            buildString {
                val (lat, lng) = GeoUtils.approximateCoordinates(it.latitude, it.longitude).split(", ")
                append("Latitude $lat, longitude $lng (position arrondie à environ 1 km)")
                it.altitude?.let { a -> append(", altitude ${a.toInt()} m") }
            }
        } ?: "Position GPS non disponible."

        val language = input.language

        return """
Tu es un expert en botanique et mycologie. Analyse la ou les photo(s) fournie(s) d'une plante,
d'un arbre ou d'un champignon et produis une identification.

LANGUE DE RÉPONSE : rédige TOUTES les valeurs textuelles du JSON en ${language.aiName}
(${language.endonym}) — noms communs, état de santé, recommandations, habitat, description,
comestibilité, libellés et périodes du calendrier, méthodes de multiplication, usages, symbolique, raison des photos
complémentaires. Les CLÉS du JSON et les valeurs codées (domain, organ) restent telles quelles, en
anglais. Les noms scientifiques restent en latin.

Contexte :
- Résultats de l'API Pl@ntNet (peu fiable ou absent pour les champignons) :
$plantNet
- Lieu de la prise de vue : $geo
  Utilise la région, l'altitude et le climat comme critères complémentaires de plausibilité.
- Date du jour : ${today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))}.

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
9. Renseigne propagation : les façons d'obtenir de nouveaux sujets à partir de cette plante
   (semis, bouturage de tige/de racine/de feuille, marcottage, division de touffe, drageons,
   stolons, greffe, bulbilles…), en ne retenant que celles qui fonctionnent vraiment pour cette
   espèce et en commençant par la plus facile pour un amateur. Pour chacune, explique en deux à
   quatre phrases comment faire (quoi prélever, préparation, substrat, conditions de reprise, délai)
   et donne la meilleure période si elle compte. Pour un champignon, décris la culture si elle est
   praticable par un amateur (ex. mycélium sur substrat), sinon liste vide. Liste vide aussi si tu
   ne peux rien affirmer de fiable.
10. Renseigne symbolism : la signification symbolique, culturelle, religieuse ou dans le langage des
    fleurs, si l'espèce en porte une (une à trois phrases, en citant la culture concernée). Mets null
    si l'espèce n'a pas de charge symbolique connue — n'en invente aucune.
11. Donne une confiance globale sur 100 tenant compte de l'accord/désaccord avec Pl@ntNet.
12. Renseigne photoSuggestions dès que tu fournis des hypothèses alternatives ou que Pl@ntNet
    propose une autre espèce que la tienne — même si tu es sûr de ton choix : c'est l'application qui
    décide, selon le score final, de montrer ces suggestions à l'utilisateur quand l'identification
    reste incertaine. Donne une à trois photos complémentaires qui départageraient ton choix des
    autres hypothèses, la plus utile d'abord : le caractère qui fonde ta certitude, s'il n'est pas
    visible, est exactement ce que l'utilisateur doit photographier pour la vérifier. Pour chacune,
    indique l'organe (organ) et, dans reason, en une ou deux phrases, ce qu'il faut cadrer et ce que
    cela permettra de distinguer (ex. « Le dessous d'une feuille : sa pilosité distingue X de Y »).
    Un organe ou un détail absent des photos fournies mais que la plante porte probablement (dessous
    des feuilles, stolons, base de la tige, écorce…) est précisément ce qu'il faut demander.
    SAISON : la photo serait prise maintenant, à la date du jour et au lieu indiqués (tiens compte de
    l'hémisphère, du climat, et d'une culture en intérieur si c'est manifestement le cas). Ne demande
    une fleur, un fruit, une graine ou un chaton que si l'espèce en porte probablement à cette date ;
    sinon, cherche un autre caractère photographiable dès maintenant qui départage aussi (feuilles et
    leur revers, pilosité, bourgeons, écorce, tige, stolons, port). Pour ces photos faisables
    maintenant, period = null.
    HORS SAISON : si une fleur, un fruit, une graine ou un chaton départagerait aussi mais n'est
    probablement pas visible à cette date, ajoute-le quand même, en dernier et au plus un seul, avec
    period = la période où il l'est (ex. « mai à août ») et une raison d'une seule phrase courte
    (ex. « Les fleurs, blanches en étoile, confirmeraient C. comosum. ») : la plante en porte
    peut-être déjà.
    Ne propose pas d'organe déjà net sur les photos. Liste vide s'il n'y a aucune hypothèse
    concurrente, ou si aucun caractère ne départagerait les hypothèses.
13. Fournis 2 à 3 hypothèses alternatives si tu n'es pas certain. Pour CHACUNE, renseigne toxic :
    true si l'espèce est toxique ou vénéneuse pour l'humain, false si elle ne l'est pas, null si tu
    ne peux pas te prononcer. C'est essentiel : l'application avertit l'utilisateur qu'il pourrait
    s'agir d'une espèce toxique lorsqu'une hypothèse toxique reste plausible.

Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour, au format exact suivant :
{
  "commonName": "nom commun dans la langue de réponse",
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
  "careCalendar": [{"label":"opération (plantation, semis, taille, arrosage, fertilisation, division, protection hivernale, récolte, cueillette…), traduite dans la langue de réponse","period":"période, ex. mars à avril","note":"précision courte" | null}],
"propagation": [{"label":"méthode (semis, bouturage de tige, division…), dans la langue de réponse","howTo":"marche à suivre en 2 à 4 phrases","period":"meilleure période, ex. fin d'été" | null}],
  "uses": [{"domain":"medicinal|food|cosmetic|chemical|craft|ornamental|ecological|other","detail":"usage en une phrase"}],
  "symbolism": "signification symbolique/culturelle et culture concernée" | null,
  "photoSuggestions": [{"organ":"leaf|flower|fruit|bark|habit|cap|gills|other","reason":"quoi cadrer et ce que cela départagera","period":"période de visibilité si hors saison, ex. mai à août" | null}]
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
        // Un objet JSON valide n'est pas pour autant une identification. Sans espèce, la fiche serait
        // enregistrée comme un succès (« Espèce inconnue », score 0) et écarterait un candidat
        // Pl@ntNet pourtant bon, sans que le repli passe au fournisseur suivant. Une réponse bloquée
        // ou un `{}` doivent compter comme un échec.
        if (dto.scientificName.isNullOrBlank()) {
            throw AiException(AiFailureReason.PARSE, "Réponse IA sans nom d'espèce.")
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
        // Repli vide et non traduit : la couche data ne connaît pas la langue d'affichage. C'est
        // l'interface qui substitue « Espèce inconnue » / « État non évalué » à un champ vide.
        commonName = commonName?.takeIf { it.isNotBlank() } ?: scientificName.orEmpty(),
        scientificName = scientificName?.takeIf { it.isNotBlank() }.orEmpty(),
        confidence = confidence?.coerceIn(0, 100) ?: 0,
        isFungus = isFungus ?: false,
        isProtected = isProtected ?: false,
        alternatives = alternatives.orEmpty().mapNotNull {
            val sci = it.scientificName ?: return@mapNotNull null
            SpeciesCandidate(sci, it.commonName, (it.score ?: 0).coerceIn(0, 100), toxic = it.toxic)
        },
        healthStatus = health?.status?.takeIf { it.isNotBlank() }.orEmpty(),
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
        propagation = propagation.orEmpty().mapNotNull {
            val label = it.label?.takeIf { l -> l.isNotBlank() } ?: return@mapNotNull null
            val howTo = it.howTo?.takeIf { h -> h.isNotBlank() } ?: return@mapNotNull null
            PropagationMethod(label, howTo, it.period?.takeIf { p -> p.isNotBlank() })
        },
        uses = uses.orEmpty().mapNotNull {
            val detail = it.detail?.takeIf { d -> d.isNotBlank() } ?: return@mapNotNull null
            SpeciesUse(UseDomain.fromCode(it.domain), detail)
        },
        symbolism = symbolism?.takeIf { it.isNotBlank() },
        // Une suggestion sans raison ne dit pas quoi photographier : écartée. Au plus trois, la
        // carte doit rester une invite et non une liste de corvées.
        photoSuggestions = photoSuggestions.orEmpty().mapNotNull {
            val reason = it.reason?.takeIf { r -> r.isNotBlank() } ?: return@mapNotNull null
            ComplementaryPhotoRequest(mapOrgan(it.organ), reason, it.period?.takeIf { p -> p.isNotBlank() })
        }.take(3),
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
        val propagation: List<PropagationDto>? = null,
        val symbolism: String? = null,
        val photoSuggestions: List<ComplementaryDto>? = null,
    )

    @Serializable
    private data class CareTaskDto(
        val label: String? = null,
        val period: String? = null,
        val note: String? = null,
    )

    @Serializable
    private data class PropagationDto(
        val label: String? = null,
        val howTo: String? = null,
        val period: String? = null,
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
        val period: String? = null,
    )
}
