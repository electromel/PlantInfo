package ch.electromel.plantinfo.data.keys

import ch.electromel.plantinfo.TestStrings
import ch.electromel.plantinfo.data.remote.ai.AiFailureReason
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * L'alerte de démarrage n'a de valeur que si elle ne crie pas au loup : une panne de réseau ne doit
 * jamais faire passer une clé valide pour révoquée, sous peine de pousser l'utilisateur à en
 * regénérer une pour rien.
 */
class KeyHealthVerdictTest {

    @Test
    fun `un succes vaut cle valide`() {
        assertEquals(KeyHealth.VALID, KeyHealthMonitor.verdictFor(null as PlantNetError?).health)
        assertEquals(KeyHealth.VALID, KeyHealthMonitor.verdictFor(null as AiFailureReason?).health)
    }

    @Test
    fun `un echec reseau ou serveur ne condamne jamais la cle`() {
        listOf(PlantNetError.NETWORK, PlantNetError.SERVER).forEach {
            assertEquals(KeyHealth.UNVERIFIABLE, KeyHealthMonitor.verdictFor(it).health)
        }
        listOf(AiFailureReason.NETWORK, AiFailureReason.SERVER).forEach {
            assertEquals(KeyHealth.UNVERIFIABLE, KeyHealthMonitor.verdictFor(it).health)
        }
    }

    @Test
    fun `une reponse illisible ne condamne pas la cle non plus`() {
        listOf(AiFailureReason.PARSE, AiFailureReason.UNKNOWN).forEach {
            assertEquals(KeyHealth.UNVERIFIABLE, KeyHealthMonitor.verdictFor(it).health)
        }
    }

    @Test
    fun `refus, credit epuise et quota rendent la cle inutilisable`() {
        listOf(
            AiFailureReason.INVALID_KEY,
            AiFailureReason.BILLING,
            AiFailureReason.QUOTA,
            AiFailureReason.MISSING_KEY,
        ).forEach {
            assertEquals("motif $it", KeyHealth.INVALID, KeyHealthMonitor.verdictFor(it).health)
        }
        assertEquals(KeyHealth.INVALID, KeyHealthMonitor.verdictFor(PlantNetError.INVALID_KEY).health)
        assertEquals(KeyHealth.INVALID, KeyHealthMonitor.verdictFor(PlantNetError.QUOTA).health)
    }

    @Test
    fun `chaque verdict inutilisable porte un motif affichable`() {
        val strings = TestStrings()
        listOf(AiFailureReason.INVALID_KEY, AiFailureReason.BILLING, AiFailureReason.QUOTA)
            .forEach { reason ->
                val issue = KeyHealthMonitor.verdictFor(reason).issue
                assertNotNull("motif $reason", issue)
                // Le motif est persisté sous forme de code : c'est sa traduction qui est montrée.
                assertEquals("motif $reason", true, strings.get(issue!!.labelRes).isNotBlank())
            }
    }
}
