package ch.electromel.plantinfo.data.keys

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pré-remplissage des clés de dev : une fois, et jamais après un effacement. */
class DevKeyPrefillTest {

    @Test
    fun `premier lancement sans cle, valeur de dev presente - on pre-remplit`() {
        assertTrue(shouldPrefill(handled = false, existing = null, default = "sk-dev"))
    }

    @Test
    fun `cle effacee par l'utilisateur - la valeur de dev ne revient pas`() {
        assertFalse(shouldPrefill(handled = true, existing = null, default = "sk-dev"))
    }

    @Test
    fun `cle deja saisie ou pas de valeur de dev - rien a faire`() {
        assertFalse(shouldPrefill(handled = false, existing = "sk-user", default = "sk-dev"))
        assertFalse(shouldPrefill(handled = false, existing = null, default = ""))
    }
}
