package com.plantinfo.domain

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
