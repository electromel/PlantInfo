package ch.electromel.plantinfo.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reconnaissance indicative d'un genre fongique, utilisée pour lever l'avertissement mycologique. */
class FungusCheckerTest {

    @Test
    fun `les genres mortels et les comestibles reputes sont reconnus`() {
        assertTrue(FungusChecker.isFungus("Amanita phalloides"))
        assertTrue(FungusChecker.isFungus("Cantharellus cibarius"))
        assertTrue(FungusChecker.isFungus("Boletus edulis"))
        assertTrue(FungusChecker.isFungus("Morchella esculenta"))
    }

    @Test
    fun `une plante n'est pas prise pour un champignon`() {
        assertFalse(FungusChecker.isFungus("Allium ursinum"))
        assertFalse(FungusChecker.isFungus("Colchicum autumnale"))
        assertFalse(FungusChecker.isFungus("Quercus robur"))
    }

    @Test
    fun `casse et espaces superflues sont ignorees`() {
        assertTrue(FungusChecker.isFungus("  amanita   muscaria "))
        assertTrue(FungusChecker.isFungus("BOLETUS EDULIS"))
    }

    @Test
    fun `un nom vide ne declenche rien`() {
        assertFalse(FungusChecker.isFungus(""))
        assertFalse(FungusChecker.isFungus("   "))
    }

    @Test
    fun `les genres fongiques toxiques de ToxicSpeciesChecker sont tous couverts`() {
        // Une espèce signalée toxique et fongique doit aussi déclencher l'avertissement mycologique.
        listOf("Galerina marginata", "Lepiota brunneoincarnata", "Cortinarius rubellus",
            "Gyromitra esculenta", "Inocybe erubescens", "Omphalotus olearius").forEach {
            assertTrue(it, ToxicSpeciesChecker.isToxic(it))
            assertTrue(it, FungusChecker.isFungus(it))
        }
    }
}
