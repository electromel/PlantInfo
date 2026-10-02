package ch.electromel.plantinfo.data.repo

import ch.electromel.plantinfo.TestStrings
import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.testEntity
import ch.electromel.plantinfo.util.ImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Supprimer, c'est aussi effacer les photos : ni plus (fiches voisines), ni moins (fiches masquées). */
class HistoryRepositoryDeletionTest {

    private val dao = mockk<IdentificationDao>(relaxed = true)
    private val imageStorage = mockk<ImageStorage>(relaxed = true)
    private val repository = HistoryRepository(dao, imageStorage, TestStrings())

    @Test
    fun `tout supprimer efface les photos de toutes les fiches, pas seulement des fiches affichees`() = runTest {
        // Trois fiches en base ; un filtre actif n'en afficherait qu'une. Avant, seules les photos de
        // la liste affichée étaient effacées alors que la table entière était vidée : les autres
        // restaient sur l'appareil, hors de portée de l'application.
        coEvery { dao.getAll() } returns listOf(
            testEntity(id = 1, photoPaths = listOf("/photos/a.jpg", "/photos/a2.jpg")),
            testEntity(id = 2, photoPaths = listOf("/photos/b.jpg")),
            testEntity(id = 3, photoPaths = listOf("/photos/c.jpg")),
        )

        repository.deleteAll()

        listOf("/photos/a.jpg", "/photos/a2.jpg", "/photos/b.jpg", "/photos/c.jpg").forEach {
            verify(exactly = 1) { imageStorage.delete(it) }
        }
        coVerify(exactly = 1) { dao.deleteAll() }
    }

    @Test
    fun `les photos sont lues avant que la table soit videe`() = runTest {
        coEvery { dao.getAll() } returns listOf(testEntity(photoPaths = listOf("/photos/a.jpg")))

        repository.deleteAll()

        coVerifyOrder {
            dao.getAll()
            dao.deleteAll()
        }
    }

    @Test
    fun `tout supprimer sans fiche ne touche a aucun fichier`() = runTest {
        coEvery { dao.getAll() } returns emptyList()

        repository.deleteAll()

        verify(exactly = 0) { imageStorage.delete(any()) }
        coVerify(exactly = 1) { dao.deleteAll() }
    }

    @Test
    fun `supprimer une fiche n'efface que ses photos`() = runTest {
        val target = testEntity(id = 1, photoPaths = listOf("/photos/a.jpg", "/photos/a2.jpg"))

        repository.delete(target)

        verify(exactly = 1) { imageStorage.delete("/photos/a.jpg") }
        verify(exactly = 1) { imageStorage.delete("/photos/a2.jpg") }
        verify(exactly = 2) { imageStorage.delete(any()) }
        coVerify { dao.delete(target) }
    }
}
