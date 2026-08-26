package ch.electromel.plantinfo.data.keys

import ch.electromel.plantinfo.data.remote.ai.AiOrchestrator
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetClient
import javax.inject.Inject
import javax.inject.Singleton

/** État de test d'une clé (appel de validation immédiat, §3.1). */
sealed interface KeyTestState {
    data object Idle : KeyTestState
    data object Testing : KeyTestState
    data object Valid : KeyTestState
    data class Invalid(val message: String) : KeyTestState
}

/**
 * Enregistre une clé saisie puis la vérifie réellement chez le fournisseur.
 *
 * Partagé par l'écran Paramètres et l'assistant de configuration : les deux saisissent des clés, et
 * un test payé au fournisseur ne doit exister qu'en un seul exemplaire dans le code — c'est aussi la
 * seule façon de garantir que les deux écrans reversent leur verdict au [KeyHealthMonitor] au lieu
 * de le laisser refaire l'appel au prochain lancement.
 */
@Singleton
class ApiKeyTester @Inject constructor(
    private val keyStore: ApiKeyStore,
    private val aiOrchestrator: AiOrchestrator,
    private val plantNetClient: PlantNetClient,
    private val keyHealth: KeyHealthMonitor,
) {
    /**
     * Enregistre la clé (elle doit l'être avant le test : c'est elle que le pipeline utilisera) puis
     * la teste. Le verdict est reversé au moniteur de santé, dont l'alerte de démarrage disparaît
     * ainsi dès la correction.
     */
    suspend fun saveAndTest(provider: ApiProvider, key: String): KeyTestState {
        keyStore.setKey(provider, key)
        val verdict = test(provider, key)
        keyHealth.record(provider, verdict)
        return verdict.toTestState()
    }

    private suspend fun test(provider: ApiProvider, key: String): KeyHealthMonitor.Verdict =
        if (provider == ApiProvider.PLANTNET) {
            KeyHealthMonitor.verdictFor(plantNetClient.testKey(key))
        } else {
            // Tout fournisseur non-Pl@ntNet est un fournisseur IA ; un type absent ne peut venir que
            // d'une entrée ajoutée sans être câblée — on ne condamne alors pas la clé.
            val type = provider.aiType
            if (type == null) {
                KeyHealthMonitor.Verdict(KeyHealth.UNVERIFIABLE, null)
            } else {
                KeyHealthMonitor.verdictFor(aiOrchestrator.testKey(type, key))
            }
        }

    /**
     * Traduit le verdict en retour d'écran. [KeyHealth.UNVERIFIABLE] n'est pas un échec de la clé :
     * le message doit désigner le réseau ou le service, pas accuser la clé qu'on vient de saisir.
     */
    private fun KeyHealthMonitor.Verdict.toTestState(): KeyTestState = when (health) {
        KeyHealth.VALID -> KeyTestState.Valid
        KeyHealth.INVALID -> KeyTestState.Invalid(
            reason?.replaceFirstChar { it.uppercase() } ?: "Clé refusée.",
        )
        KeyHealth.UNVERIFIABLE, KeyHealth.UNKNOWN -> KeyTestState.Invalid(
            "Impossible de vérifier la clé pour l'instant (réseau ou service indisponible). " +
                "Elle est enregistrée : réessayez plus tard.",
        )
    }
}
