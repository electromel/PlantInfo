package ch.electromel.plantinfo.domain

import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.iucnStatus

/**
 * Renforce les drapeaux de sécurité d'un résultat d'identification avec les listes locales, quelle
 * que soit leur origine (IA, Pl@ntNet seul, relance).
 *
 * Les trois listes — [ToxicSpeciesChecker], [FungusChecker], [ProtectedSpeciesChecker] — promettent
 * de **renforcer sans jamais désactiver** : « si l'IA déclare inoffensive une espèce listée, c'est la
 * liste qui l'emporte ». Cette promesse ne tenait que pour les hypothèses alternatives et la
 * validation manuelle. Pour l'espèce **principale** d'une identification, le verdict reposait sur la
 * seule parole de l'IA : une IA qui se trompe (ou une identification sans IA, Pl@ntNet seul)
 * n'affichait ni alerte de toxicité ni bandeau champignon. Cette fonction ferme ce trou, au point
 * par où passent l'enregistrement et la relance.
 *
 * Pure et idempotente : l'appliquer deux fois donne le même résultat.
 */
object SafetyFlags {

    fun reinforce(result: IdentificationResult): IdentificationResult {
        val name = result.scientificName

        // Une espèce menacée au sens UICN n'est pas juridiquement protégée pour autant (le statut est
        // mondial, la protection cantonale ou fédérale) ; on l'assimile volontairement, le but du
        // drapeau étant de déconseiller la cueillette. Le statut UICN reste affiché tel quel.
        val protected = result.isProtected ||
            ProtectedSpeciesChecker.isProtected(name) ||
            result.iucnStatus?.threatened == true

        // `toxic` ne passe jamais de true à false ni de null à false : « non toxique » serait une
        // affirmation que personne n'a faite. La toxicité prime sur la comestibilité à l'affichage
        // (voir edibilityVerdict), un « comestible » de l'IA ne masque donc pas la liste.
        val toxic = if (result.toxic == true || ToxicSpeciesChecker.isToxic(name)) true else result.toxic

        val fungus = result.isFungus || FungusChecker.isFungus(name)

        return if (protected == result.isProtected && toxic == result.toxic && fungus == result.isFungus) {
            result
        } else {
            result.copy(isProtected = protected, toxic = toxic, isFungus = fungus)
        }
    }
}
