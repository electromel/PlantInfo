package ch.electromel.plantinfo.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `masque la cle Pl@ntNet en tete de requete`() {
        val line = "--> POST https://my-api.plantnet.org/v2/identify/all?api-key=2b10SECRET&nb-results=5&lang=fr"
        assertEquals(
            "--> POST https://my-api.plantnet.org/v2/identify/all?api-key=██&nb-results=5&lang=fr",
            LogRedactor.redact(line),
        )
    }

    @Test
    fun `masque la cle Pl@ntNet dans la ligne de reponse`() {
        val line = "<-- 400 https://my-api.plantnet.org/v2/identify/all?api-key=2b10SECRET (120ms, 45-byte body)"
        val out = LogRedactor.redact(line)
        assertFalse(out.contains("2b10SECRET"))
        assertEquals("<-- 400 https://my-api.plantnet.org/v2/identify/all?api-key=██ (120ms, 45-byte body)", out)
    }

    @Test
    fun `masque un parametre key place apres un autre`() {
        val line = "<-- 200 https://example.org/x?alt=json&key=AIzaSECRET"
        assertEquals("<-- 200 https://example.org/x?alt=json&key=██", LogRedactor.redact(line))
    }

    @Test
    fun `laisse intacts les parametres sans secret`() {
        val line = "--> GET https://api.gbif.org/v1/occurrence/search?taxonKey=123&limit=300"
        assertEquals(line, LogRedactor.redact(line))
    }
}
