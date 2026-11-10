package ch.electromel.plantinfo.data.keys

import android.content.Context
import android.content.SharedPreferences
import ch.electromel.plantinfo.data.remote.ai.AiFailureReason
import ch.electromel.plantinfo.data.remote.ai.AiOrchestrator
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetClient
import ch.electromel.plantinfo.domain.model.AiProviderType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Surveillance des clés et mode « Gemini seul » (constat F11 de l'audit du 2026-11-10) : une clé
 * que l'application n'utilise pas ne doit ni coûter un appel quotidien, ni déclencher d'alerte.
 */
class KeyHealthMonitorTest {

    private val prefs = InMemoryPrefs()
    private val context = mockk<Context> { every { getSharedPreferences(any(), any()) } returns prefs }
    private val keyStore = mockk<ApiKeyStore>()
    private val orchestrator = mockk<AiOrchestrator>()
    private val plantNet = mockk<PlantNetClient>()

    private var geminiOnly = true

    private fun monitor(): KeyHealthMonitor {
        every { keyStore.freeGeminiOnly() } answers { geminiOnly }
        every { keyStore.getKey(any()) } answers {
            when (firstArg<ApiProvider>()) {
                ApiProvider.CLAUDE -> "cle-claude"
                ApiProvider.GEMINI -> "cle-gemini"
                else -> null
            }
        }
        coEvery { orchestrator.testKey(AiProviderType.CLAUDE, any()) } returns AiFailureReason.BILLING
        coEvery { orchestrator.testKey(AiProviderType.GEMINI, any()) } returns null
        return KeyHealthMonitor(context, keyStore, orchestrator, plantNet)
    }

    @Test
    fun `en mode Gemini seul, une cle ecartee n'est ni testee ni signalee`() = runBlocking {
        val monitor = monitor()

        monitor.refresh()

        coVerify(exactly = 0) { orchestrator.testKey(AiProviderType.CLAUDE, any()) }
        coVerify(exactly = 1) { orchestrator.testKey(AiProviderType.GEMINI, any()) }
        assertTrue(monitor.problems.value.isEmpty())
    }

    @Test
    fun `un verdict deja connu pour une cle ecartee n'est plus signale, puis revient si le mode change`() = runBlocking {
        geminiOnly = false
        val monitor = monitor()
        monitor.refresh()
        assertEquals(
            listOf(KeyProblem(ApiProvider.CLAUDE, KeyIssue.BILLING)),
            monitor.problems.value,
        )

        geminiOnly = true
        monitor.onProviderSelectionChanged()
        assertTrue(monitor.problems.value.isEmpty())

        // Le verdict n'a pas été oublié : il resurgit sans nouvel appel au fournisseur.
        geminiOnly = false
        monitor.onProviderSelectionChanged()
        assertEquals(ApiProvider.CLAUDE, monitor.problems.value.single().provider)
        coVerify(exactly = 1) { orchestrator.testKey(AiProviderType.CLAUDE, any()) }
    }

    @Test
    fun `une reverification demandee teste aussi la cle ecartee, sans la signaler`() = runBlocking {
        val monitor = monitor()

        monitor.refresh(force = true)

        coVerify(exactly = 1) { orchestrator.testKey(AiProviderType.CLAUDE, any()) }
        assertTrue(monitor.problems.value.isEmpty())
    }

    @Test
    fun `la cle Gemini reste surveillee en mode Gemini seul`() = runBlocking {
        val monitor = monitor()
        coEvery { orchestrator.testKey(AiProviderType.GEMINI, any()) } returns AiFailureReason.INVALID_KEY

        monitor.refresh()

        assertEquals(
            listOf(KeyProblem(ApiProvider.GEMINI, KeyIssue.INVALID_KEY)),
            monitor.problems.value,
        )
    }
}

/** Préférences en mémoire, limitées à ce que [KeyHealthMonitor] utilise (chaînes et entiers longs). */
private class InMemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = throw UnsupportedOperationException()
    override fun getInt(key: String, defValue: Int) = throw UnsupportedOperationException()
    override fun getFloat(key: String, defValue: Float) = throw UnsupportedOperationException()
    override fun getBoolean(key: String, defValue: Boolean) = throw UnsupportedOperationException()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun putStringSet(key: String, v: MutableSet<String>?) = throw UnsupportedOperationException()
        override fun putInt(key: String, value: Int) = throw UnsupportedOperationException()
        override fun putFloat(key: String, value: Float) = throw UnsupportedOperationException()
        override fun putBoolean(key: String, value: Boolean) = throw UnsupportedOperationException()
        override fun commit(): Boolean = true
        override fun apply() = Unit
    }
}
