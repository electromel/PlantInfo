package ch.electromel.plantinfo.data.repo

import android.content.Context
import ch.electromel.plantinfo.TestStrings
import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.remote.ai.AiOrchestrator
import ch.electromel.plantinfo.data.remote.ai.AiOutcome
import ch.electromel.plantinfo.data.remote.ai.AiPrompt
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetClient
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetResult
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.EdibilityVerdict
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import ch.electromel.plantinfo.domain.model.IdentificationRequest
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.edibilityVerdict
import ch.electromel.plantinfo.testEntity
import ch.electromel.plantinfo.util.AppLanguage
import ch.electromel.plantinfo.util.AppLocales
import ch.electromel.plantinfo.util.ImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pipeline d'identification de bout en bout, avec des dépendances simulées : ce qui est enregistré,
 * dans quelle langue on interroge Pl@ntNet, et ce que devient une fiche modifiée pendant sa relance.
 */
class IdentificationRepositoryTest {

    private val keyStore = mockk<ApiKeyStore>()
    private val plantNet = mockk<PlantNetClient>()
    private val ai = mockk<AiOrchestrator>()
    private val imageStorage = mockk<ImageStorage>()
    private val dao = mockk<IdentificationDao>()
    private val context = mockk<Context>(relaxed = true)
    private val repository = IdentificationRepository(
        keyStore, plantNet, ai, imageStorage, dao, TestStrings(), context,
    )

    private val request = IdentificationRequest(
        photoPaths = listOf("/photos/a.jpg"),
        organs = listOf(PhotoOrgan.HABIT),
        gps = null,
    )

    @Before
    fun setUp() {
        mockkObject(AppLocales)
        every { AppLocales.current(any()) } returns AppLanguage.GERMAN
        every { imageStorage.readBytes(any()) } returns byteArrayOf(1, 2, 3)
        every { keyStore.getKey(ApiProvider.PLANTNET) } returns "cle-plantnet"
        every { keyStore.availableAiProvidersInOrder() } returns listOf(AiProviderType.GEMINI)
    }

    @After
    fun tearDown() {
        unmockkObject(AppLocales)
    }

    private fun plantNetReturns(vararg candidates: SpeciesCandidate) {
        coEvery { plantNet.identify(any(), any(), any(), any()) } returns PlantNetResult(candidates.toList(), null)
    }

    private fun aiReturns(json: String) {
        coEvery { ai.analyze(any()) } returns AiOutcome.Success(AiPrompt.parse(json), AiProviderType.GEMINI)
    }

    private fun capturedInsert(): IdentificationEntity {
        val inserted = slot<IdentificationEntity>()
        coEvery { dao.insert(capture(inserted)) } returns 42L
        return runBlocking {
            val outcome = repository.identifyAndSave(request)
            assertTrue("succès attendu : $outcome", outcome is IdentificationOutcome.Success)
            inserted.captured
        }
    }

    // --- Sécurité alimentaire sur l'espèce principale ---

    @Test
    fun `sans IA, une espece toxique de la liste est enregistree toxique`() {
        plantNetReturns(SpeciesCandidate("Colchicum autumnale", "Colchique d'automne", 91))
        coEvery { ai.analyze(any()) } returns AiOutcome.NoProvidersConfigured

        val saved = capturedInsert()

        assertEquals(true, saved.toxic)
        assertEquals(EdibilityVerdict.TOXIC, saved.toResult().edibilityVerdict)
    }

    @Test
    fun `une IA qui declare comestible une amanite est contredite avant l'enregistrement`() {
        plantNetReturns()
        aiReturns(
            """{"commonName":"Amanite phalloïde","scientificName":"Amanita phalloides","confidence":88,
                "isFungus":false,"edible":true,"toxic":false}""",
        )

        val saved = capturedInsert()

        assertEquals(true, saved.toxic)
        assertTrue("l'avertissement champignon ne dépend plus du seul jugement de l'IA", saved.isFungus)
        assertEquals(EdibilityVerdict.TOXIC, saved.toResult().edibilityVerdict)
    }

    @Test
    fun `une espece benigne n'est pas declaree toxique`() {
        plantNetReturns(SpeciesCandidate("Rosa canina", "Eglantier", 80))
        aiReturns("""{"scientificName":"Rosa canina","confidence":80}""")

        val saved = capturedInsert()

        assertNull(saved.toxic)
        assertTrue(!saved.isFungus)
    }

    // --- Langue ---

    @Test
    fun `Pl@ntNet est interroge dans la langue de l'application`() {
        plantNetReturns(SpeciesCandidate("Rosa canina", "Hunds-Rose", 80))
        aiReturns("""{"scientificName":"Rosa canina","confidence":80}""")

        capturedInsert()

        coVerify { plantNet.identify(any(), any(), "cle-plantnet", "de") }
    }

    // --- Relance : ce que l'utilisateur modifie pendant l'analyse ne doit pas disparaître ---

    private val stale = testEntity(id = 7, notes = "ancienne note", isFavorite = false)

    @Test
    fun `une note et un favori modifies pendant la relance sont conserves`() {
        plantNetReturns(SpeciesCandidate("Rosa canina", "Eglantier", 80))
        aiReturns("""{"scientificName":"Rosa canina","confidence":80}""")
        // Pendant l'analyse, l'utilisateur a écrit une note et mis la fiche en favori.
        coEvery { dao.getById(7) } returns stale.copy(notes = "note écrite pendant l'analyse", isFavorite = true)
        val updated = slot<IdentificationEntity>()
        coEvery { dao.update(capture(updated)) } returns Unit

        val outcome = runBlocking { repository.reanalyzeAndUpdate(stale) }

        assertTrue(outcome is IdentificationOutcome.Success)
        assertEquals("note écrite pendant l'analyse", updated.captured.notes)
        assertEquals(true, updated.captured.isFavorite)
        assertEquals(7L, updated.captured.id)
        assertEquals(stale.dateTime, updated.captured.dateTime)
        assertEquals(stale.photoPaths, updated.captured.photoPaths)
    }

    @Test
    fun `une fiche supprimee pendant la relance n'est pas recreee`() {
        plantNetReturns(SpeciesCandidate("Rosa canina", "Eglantier", 80))
        aiReturns("""{"scientificName":"Rosa canina","confidence":80}""")
        coEvery { dao.getById(7) } returns null

        val outcome = runBlocking { repository.reanalyzeAndUpdate(stale) }

        assertTrue(outcome is IdentificationOutcome.Success)
        coVerify(exactly = 0) { dao.update(any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `la relance applique elle aussi les listes de securite`() {
        plantNetReturns(SpeciesCandidate("Colchicum autumnale", "Colchique", 90))
        coEvery { ai.analyze(any()) } returns AiOutcome.NoProvidersConfigured
        coEvery { dao.getById(7) } returns stale
        val updated = slot<IdentificationEntity>()
        coEvery { dao.update(capture(updated)) } returns Unit

        runBlocking { repository.reanalyzeAndUpdate(stale) }

        assertEquals(true, updated.captured.toxic)
    }

    @Test
    fun `ajouter une photo repart de la ligne en base, pas de l'instantane de l'ecran`() {
        plantNetReturns(SpeciesCandidate("Rosa canina", "Eglantier", 80))
        aiReturns("""{"scientificName":"Rosa canina","confidence":80}""")
        val fresh = stale.copy(notes = "note plus récente")
        coEvery { dao.getById(7) } returns fresh
        val writes = mutableListOf<IdentificationEntity>()
        coEvery { dao.update(capture(writes)) } returns Unit

        runBlocking { repository.addPhotoAndReanalyze(stale, "/photos/new.jpg", PhotoOrgan.LEAF) }

        // Première écriture : la photo ajoutée, sans écraser la note plus récente.
        assertEquals(listOf("/data/photo1.jpg", "/photos/new.jpg"), writes.first().photoPaths)
        assertEquals("note plus récente", writes.first().notes)
        // Les deux photos partent à l'analyse.
        coVerify { plantNet.identify(match { it.size == 2 }, any(), any(), any()) }
    }

    @Test
    fun `aucune cle du tout n'appelle aucun service`() {
        every { keyStore.getKey(ApiProvider.PLANTNET) } returns null
        every { keyStore.availableAiProvidersInOrder() } returns emptyList()

        val outcome = runBlocking { repository.identifyAndSave(request) }

        assertTrue(outcome is IdentificationOutcome.Failure)
        coVerify(exactly = 0) { plantNet.identify(any(), any(), any(), any()) }
        coVerify(exactly = 0) { ai.analyze(any()) }
    }
}
