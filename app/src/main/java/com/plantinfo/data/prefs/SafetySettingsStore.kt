package com.plantinfo.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.plantinfo.domain.model.ToxicAlertThresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réglages de sécurité modifiables par l'utilisateur (§2.4) : seuils de l'avertissement de confusion
 * toxique.
 *
 * Préférences **en clair**, contrairement à `ApiKeyStore` : un seuil n'est pas un secret, et le
 * chiffrement Keystore n'apporterait ici qu'un coût. Lecture **synchrone** : l'avertissement est
 * calculé au moment de composer la fiche, d'exporter un PDF ou de partager un texte, sans point de
 * suspension à ces endroits.
 *
 * Les valeurs sont bornées à l'écriture **et** à la lecture : une préférence corrompue ou écrite par
 * une version future ne doit jamais désactiver l'avertissement en produisant un seuil aberrant.
 */
@Singleton
class SafetySettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("plantinfo_settings", Context.MODE_PRIVATE)

    private val _thresholds = MutableStateFlow(read())

    /** Seuils courants, observables : la fiche affichée se met à jour au retour des Paramètres. */
    val thresholds: StateFlow<ToxicAlertThresholds> = _thresholds.asStateFlow()

    /** Lecture immédiate, pour les appelants hors Compose (export PDF, partage). */
    fun current(): ToxicAlertThresholds = _thresholds.value

    fun setMaxScore(value: Int) {
        val coerced = value.coerceIn(ToxicAlertThresholds.MAX_SCORE_RANGE)
        prefs.edit().putInt(KEY_MAX_SCORE, coerced).apply()
        _thresholds.value = read()
    }

    fun setMinAlternativeScore(value: Int) {
        val coerced = value.coerceIn(ToxicAlertThresholds.MIN_ALTERNATIVE_RANGE)
        prefs.edit().putInt(KEY_MIN_ALTERNATIVE, coerced).apply()
        _thresholds.value = read()
    }

    /** Revient aux seuils livrés avec l'application. */
    fun resetToxicAlertThresholds() {
        prefs.edit().remove(KEY_MAX_SCORE).remove(KEY_MIN_ALTERNATIVE).apply()
        _thresholds.value = read()
    }

    private fun read(): ToxicAlertThresholds {
        val defaults = ToxicAlertThresholds()
        return ToxicAlertThresholds(
            maxScore = prefs.getInt(KEY_MAX_SCORE, defaults.maxScore)
                .coerceIn(ToxicAlertThresholds.MAX_SCORE_RANGE),
            minAlternativeScore = prefs.getInt(KEY_MIN_ALTERNATIVE, defaults.minAlternativeScore)
                .coerceIn(ToxicAlertThresholds.MIN_ALTERNATIVE_RANGE),
        )
    }

    private companion object {
        const val KEY_MAX_SCORE = "toxic_alert_max_score"
        const val KEY_MIN_ALTERNATIVE = "toxic_alert_min_alternative_score"
    }
}
