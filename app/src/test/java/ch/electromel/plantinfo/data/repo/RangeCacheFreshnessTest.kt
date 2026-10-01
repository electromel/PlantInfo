package ch.electromel.plantinfo.data.repo

import ch.electromel.plantinfo.data.db.SpeciesRangeCacheEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Quand une ligne du cache de répartition GBIF peut-elle être servie sans rappeler GBIF ? */
class RangeCacheFreshnessTest {

    private val now = TimeUnit.DAYS.toMillis(1000)

    private fun row(ageDays: Long, hasData: Boolean = true, usageKey: Long? = 5355395L) =
        SpeciesRangeCacheEntity(
            scientificName = "Spartium junceum",
            pointsJson = "[]",
            hasData = hasData,
            fetchedAt = now - TimeUnit.DAYS.toMillis(ageDays),
            usageKey = usageKey,
        )

    @Test
    fun `ligne recente avec sa cle - servie depuis le cache`() {
        assertTrue(isCacheFresh(row(ageDays = 10), now))
    }

    @Test
    fun `ligne de plus de 90 jours - redemandee`() {
        assertFalse(isCacheFresh(row(ageDays = 91), now))
    }

    @Test
    fun `espece connue mais sans cle (cache d'avant la v10) - redemandee pour obtenir les zones`() {
        assertFalse(isCacheFresh(row(ageDays = 10, usageKey = null), now))
    }

    @Test
    fun `espece inconnue de GBIF - la reponse aucune donnee reste valable sans cle`() {
        // Pas de clé à attendre quand GBIF ne connaît pas l'espèce : sinon on le rappellerait à chaque vue.
        assertTrue(isCacheFresh(row(ageDays = 10, hasData = false, usageKey = null), now))
    }
}
