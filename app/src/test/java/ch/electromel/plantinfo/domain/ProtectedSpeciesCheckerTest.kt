package ch.electromel.plantinfo.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedSpeciesCheckerTest {

    @Test
    fun `les orchidees sont signalees comme protegees`() {
        assertTrue(ProtectedSpeciesChecker.isProtected("Orchis militaris"))
        assertTrue(ProtectedSpeciesChecker.isProtected("Cypripedium calceolus"))
    }

    @Test
    fun `especes emblematiques protegees`() {
        assertTrue(ProtectedSpeciesChecker.isProtected("Leontopodium alpinum"))
        assertTrue(ProtectedSpeciesChecker.isProtected("Ilex aquifolium"))
    }

    @Test
    fun `insensible a la casse et aux espaces`() {
        assertTrue(ProtectedSpeciesChecker.isProtected("  gentiana  lutea "))
    }

    @Test
    fun `espece commune non signalee`() {
        assertFalse(ProtectedSpeciesChecker.isProtected("Bellis perennis"))
        assertFalse(ProtectedSpeciesChecker.isProtected("Taraxacum officinale"))
    }
}

class ToxicSpeciesCheckerTest {

    @Test
    fun `confusions mortelles classiques`() {
        // Ail des ours / colchique et muguet, gentiane jaune / vératre : les cas d'empoisonnement
        // les plus documentés en Europe.
        assertTrue(ToxicSpeciesChecker.isToxic("Colchicum autumnale"))
        assertTrue(ToxicSpeciesChecker.isToxic("Convallaria majalis"))
        assertTrue(ToxicSpeciesChecker.isToxic("Veratrum album"))
        assertTrue(ToxicSpeciesChecker.isToxic("Conium maculatum"))
    }

    @Test
    fun `champignons des genres mortels`() {
        assertTrue(ToxicSpeciesChecker.isToxic("Amanita phalloides"))
        assertTrue(ToxicSpeciesChecker.isToxic("Galerina marginata"))
        assertTrue(ToxicSpeciesChecker.isToxic("Cortinarius orellanus"))
    }

    @Test
    fun `binome toxique dans un genre par ailleurs comestible`() {
        assertTrue(ToxicSpeciesChecker.isToxic("Solanum nigrum"))
        // La tomate appartient au même genre et ne doit pas être signalée.
        assertFalse(ToxicSpeciesChecker.isToxic("Solanum lycopersicum"))
    }

    @Test
    fun `insensible a la casse et aux espaces`() {
        assertTrue(ToxicSpeciesChecker.isToxic("  aconitum  napellus "))
    }

    @Test
    fun `espece commune non signalee`() {
        assertFalse(ToxicSpeciesChecker.isToxic("Bellis perennis"))
        assertFalse(ToxicSpeciesChecker.isToxic("Quercus robur"))
        assertFalse(ToxicSpeciesChecker.isToxic(""))
    }
}
