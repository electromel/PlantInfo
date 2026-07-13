package com.plantinfo.data.remote.ai

import com.plantinfo.domain.model.AiProviderType

/**
 * Interface commune aux fournisseurs d'IA générative multimodale. Chaque implémentation encapsule
 * le format de requête spécifique (Claude, Gemini, GPT) derrière une API uniforme, ce qui permet à
 * l'orchestrateur d'appliquer la logique de repli sans connaître les détails de chaque fournisseur.
 */
interface AiProvider {
    val type: AiProviderType

    /** Analyse la photo et le contexte ; lève AiException en cas d'échec (motif typé). */
    suspend fun analyze(input: AiAnalysisInput, apiKey: String): AiAnalysis

    /**
     * Répond à une question libre (texte seul) sur une plante déjà identifiée. Le [prompt] contient
     * déjà le contexte de la plante et le lieu de prise de vue. Lève AiException en cas d'échec.
     */
    suspend fun ask(prompt: String, apiKey: String): String

    /** Appel de test léger pour valider une clé depuis l'écran Paramètres (§3.1). */
    suspend fun testKey(apiKey: String): Boolean
}
