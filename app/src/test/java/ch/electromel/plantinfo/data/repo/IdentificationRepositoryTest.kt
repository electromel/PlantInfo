package ch.electromel.plantinfo.data.repo

import android.content.Context
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.TestStrings
import ch.electromel.plantinfo.data.db.IdentificationDao
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.keys.ApiKeyStore
import ch.electromel.plantinfo.data.keys.ApiProvider
import ch.electromel.plantinfo.data.remote.ai.AiAnalysis
import ch.electromel.plantinfo.data.remote.ai.AiAnalysisInput
import ch.electromel.plantinfo.data.remote.ai.AiFailureReason
import ch.electromel.plantinfo.data.remote.ai.AiOrchestrator
import ch.electromel.plantinfo.data.remote.ai.AiOutcome
import ch.electromel.plantinfo.data.remote.ai.ProviderFailure
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetClient
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetError
import ch.electromel.plantinfo.data.remote.plantnet.PlantNetResult
import ch.electromel.plantinfo.data.remote.web.WebPhotoClient
import ch.electromel.plantinfo.domain.ConfidenceEngine
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.ComplementaryPhotoRequest
import ch.electromel.plantinfo.domain.model.FailureKind
import ch.electromel.plantinfo.domain.model.GpsLocation
import ch.electromel.plantinfo.domain.model.IdentificationOutcome
import ch.electromel.plantinfo.domain.model.IdentificationRequest
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.util.AppLanguage
import ch.electromel.plantinfo.util.AppLocales
import ch.electromel.plantinfo.util.ImageStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Pipeline d'identification : Pl@ntNet, IA avec repli, fusion, persistance, et tous les cas dégradés
 * exposés par [IdentificationOutcome].
 *
 * Les tests préfixés « DEFAUT CONNU » figent un comportement fautif relevé par l'audit du
 * 2026-11-10 (voir `log/2026.11.10-Audit v2 - rapport.md`) : la correction devra les inverser.
 */
class IdentificationRepositoryTest {

    private val keyStore = mockk<ApiKeyStore>()
    private val plantNetClient = mockk<PlantNetClient>()
    private val orchestrator = mockk<AiOrchestrator>()
    private val imageStorage = mockk<ImageStorage>(relaxed = true)
    private val webPhotos = mockk<WebPhotoClient>()
    private val dao = mockk<IdentificationDao>(relaxed = true)
    private val strings = TestStrings()

    private val repository = IdentificationRepository(
        keyStore, plantNetClient, orchestrator, imageStorage, webPhotos, dao, strings,
        mockk<Context>(relaxed = true),
    )

    private val gps = GpsLocation(46.2, 7.3, 500.0, 8f)
    private val request = IdentificationRequest(
        photoPaths = listOf("/p/1.jpg", "/p/2.jpg"),
        organs = listOf(PhotoOrgan.HABIT, PhotoOrgan.FLOWER),
        gps = gps,
    )
    private val oak = SpeciesCandidate("Quercus robur", "Chêne pédonculé", 70)

    @Before
    fun setUp() {
        // La langue de l'app se lit sur le contexte Android, absent des tests JVM.
        mockkObject(AppLocales)
        every { AppLocales.current(any()) } returns AppLanguage.FRENCH
        every { imageStorage.readBytes(any()) } returns byteArrayOf(1)
        coEvery { dao.insert(any()) } returns 42L
        coEvery { dao.getById(any()) } returns null
    }

    @After
    fun tearDown() = unmockkAll()

    // --- Mise en place ---

    private fun keys(plantNet: Boolean = true, ai: Boolean = true) {
        every { keyStore.getKey(ApiProvider.PLANTNET) } returns if (plantNet) "cle-pn" else null
        every { keyStore.availableAiProvidersInOrder() } returns
            if (ai) listOf(AiProviderType.GEMINI) else emptyList()
    }

    private fun plantNet(vararg candidates: SpeciesCandidate, error: PlantNetError? = null) {
        coEvery { plantNetClient.identify(any(), any(), any(), any()) } returns
            PlantNetResult(candidates.toList(), error)
    }

    private fun ai(outcome: AiOutcome) {
        coEvery { orchestrator.analyze(any()) } returns outcome
    }

    private fun aiSuccess(analysis: AiAnalysis) = ai(AiOutcome.Success(analysis, AiProviderType.GEMINI))

    private fun aiFailed(vararg reasons: AiFailureReason) = ai(
        AiOutcome.AllFailed(
            failures = reasons.map { ProviderFailure(AiProviderType.GEMINI, it) },
            allNetwork = reasons.all { it == AiFailureReason.NETWORK },
        ),
    )

    private fun analysis(
        scientific: String = "Quercus robur",
        confidence: Int = 80,
        toxic: Boolean? = null,
    ) = AiAnalysis(
        commonName = "Chêne",
        scientificName = scientific,
        confidence = confidence,
        isFungus = false,
        isProtected = false,
        alternatives = emptyList(),
        healthStatus = "Feuillage sain",
        isHealthy = true,
        recommendations = emptyList(),
        habitat = "Forêts de plaine",
        description = null,
        matureHeight = null,
        matureDiameter = null,
        timeToMaturity = null,
        edible = null,
        toxic = toxic,
        edibilityNote = null,
        careCalendar = emptyList(),
        uses = emptyList(),
        propagation = emptyList(),
        symbolism = null,
        photoSuggestions = listOf(ComplementaryPhotoRequest(PhotoOrgan.BARK, "L'écorce trancherait.")),
    )

    private fun inserted(): IdentificationEntity {
        val entity = slot<IdentificationEntity>()
        coVerify { dao.insert(capture(entity)) }
        return entity.captured
    }

    private fun updates(): List<IdentificationEntity> {
        val entities = mutableListOf<IdentificationEntity>()
        coVerify { dao.update(capture(entities)) }
        return entities
    }

    private fun storedFiche(): IdentificationEntity =
        ConfidenceEngine.plantNetOnly(listOf(oak))
            .toEntity(gps, listOf("/p/1.jpg"), AiProviderType.NONE, dateTime = 1_700_000_000_000)
            .copy(id = 7, isFavorite = true, notes = "près du ruisseau", userConfirmed = true)

    // --- Identification depuis des photos ---

    @Test
    fun `sans aucune cle rien n'est appele ni enregistre`() = runBlocking {
        keys(plantNet = false, ai = false)

        val outcome = repository.identifyAndSave(request) as IdentificationOutcome.Failure

        assertEquals(FailureKind.NO_KEYS, outcome.kind)
        coVerify(exactly = 0) { plantNetClient.identify(any(), any(), any(), any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `accord Pl@ntNet et IA - la fiche est enregistree avec son contexte`() = runBlocking {
        keys()
        plantNet(oak)
        aiSuccess(analysis())

        val outcome = repository.identifyAndSave(request) as IdentificationOutcome.Success

        assertEquals(42L, outcome.id)
        assertNull(outcome.infoMessage)
        val entity = inserted()
        assertEquals("Quercus robur", entity.scientificName)
        assertEquals("GEMINI", entity.aiProvider)
        assertEquals(request.photoPaths, entity.photoPaths)
        assertEquals(46.2, entity.latitude)
        assertEquals(86, entity.scoreFinal) // 0,6 × 80 + 0,4 × 70 + 10 d'accord
        assertNull(entity.queriedName)
    }

    @Test
    fun `Pl@ntNet recoit les organes et la langue, l'IA ses candidats et le lieu`() = runBlocking {
        keys()
        plantNet(oak)
        aiSuccess(analysis())

        repository.identifyAndSave(request)

        coVerify { plantNetClient.identify(any(), listOf("habit", "flower"), "cle-pn", "fr") }
        val input = slot<AiAnalysisInput>()
        coVerify { orchestrator.analyze(capture(input)) }
        assertEquals(listOf(oak), input.captured.plantNetCandidates)
        assertEquals(gps, input.captured.gps)
        assertEquals(AppLanguage.FRENCH, input.captured.language)
        assertEquals(2, input.captured.images.size)
    }

    @Test
    fun `une espece menacee selon l'UICN est marquee protegee meme si l'IA dit non`() = runBlocking {
        keys()
        plantNet(oak.copy(iucnCategory = "EN"))
        aiSuccess(analysis())

        repository.identifyAndSave(request)

        assertTrue(inserted().isProtected)
    }

    @Test
    fun `sans cle Pl@ntNet l'IA seule suffit`() = runBlocking {
        keys(plantNet = false)
        aiSuccess(analysis(confidence = 64))

        val outcome = repository.identifyAndSave(request) as IdentificationOutcome.Success

        assertEquals(64, outcome.result.scoreFinal)
        assertNull(outcome.result.scorePlantNet)
        coVerify(exactly = 0) { plantNetClient.identify(any(), any(), any(), any()) }
    }

    @Test
    fun `sans cle IA la fiche est le resultat Pl@ntNet brut, avec un message`() = runBlocking {
        keys(ai = false)
        plantNet(oak)
        ai(AiOutcome.NoProvidersConfigured)

        val outcome = repository.identifyAndSave(request) as IdentificationOutcome.Success

        assertEquals(strings.get(R.string.id_no_ai_key), outcome.infoMessage)
        val entity = inserted()
        assertEquals("NONE", entity.aiProvider)
        assertEquals(70, entity.scoreFinal)
        assertNull(entity.healthStatus)
    }

    @Test
    fun `sans cle IA et sans candidat l'echec porte le motif Pl@ntNet`() = runBlocking {
        keys(ai = false)
        ai(AiOutcome.NoProvidersConfigured)

        plantNet(error = PlantNetError.INVALID_KEY)
        val invalid = repository.identifyAndSave(request) as IdentificationOutcome.Failure
        plantNet(error = PlantNetError.QUOTA)
        val quota = repository.identifyAndSave(request) as IdentificationOutcome.Failure
        plantNet(error = PlantNetError.NETWORK)
        val network = repository.identifyAndSave(request) as IdentificationOutcome.Failure
        plantNet(error = PlantNetError.NO_MATCH)
        val noMatch = repository.identifyAndSave(request) as IdentificationOutcome.Failure

        assertEquals(FailureKind.INVALID_KEY, invalid.kind)
        assertEquals(FailureKind.QUOTA, quota.kind)
        assertEquals(FailureKind.NETWORK, network.kind)
        assertEquals(FailureKind.NO_RESULT, noMatch.kind)
        // Seul l'échec réseau justifie une mise en file.
        assertEquals(listOf(false, false, true, false), listOf(invalid, quota, network, noMatch).map { it.queuedForRetry })
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `IA en echec mais Pl@ntNet a repondu - fiche partielle enregistree`() = runBlocking {
        keys()
        plantNet(oak)
        aiFailed(AiFailureReason.QUOTA)

        val outcome = repository.identifyAndSave(request) as IdentificationOutcome.Success

        assertTrue(outcome.infoMessage!!.contains("Gemini"))
        assertEquals("NONE", inserted().aiProvider)
    }

    @Test
    fun `IA injoignable et aucun candidat - mise en file demandee`() = runBlocking {
        keys()
        plantNet(error = PlantNetError.NETWORK)
        aiFailed(AiFailureReason.NETWORK)

        val outcome = repository.identifyAndSave(request) as IdentificationOutcome.Failure

        assertEquals(FailureKind.NETWORK, outcome.kind)
        assertTrue(outcome.queuedForRetry)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `IA refusee pour quota ou cle - pas de mise en file`() = runBlocking {
        keys(plantNet = false)

        aiFailed(AiFailureReason.BILLING)
        val billing = repository.identifyAndSave(request) as IdentificationOutcome.Failure
        aiFailed(AiFailureReason.INVALID_KEY)
        val invalid = repository.identifyAndSave(request) as IdentificationOutcome.Failure
        aiFailed(AiFailureReason.PARSE)
        val parse = repository.identifyAndSave(request) as IdentificationOutcome.Failure

        assertEquals(FailureKind.QUOTA, billing.kind)
        assertEquals(FailureKind.INVALID_KEY, invalid.kind)
        assertEquals(FailureKind.SERVER, parse.kind)
        assertTrue(listOf(billing, invalid, parse).none { it.queuedForRetry })
    }

    // --- Relance et photo complémentaire ---

    @Test
    fun `la relance met la fiche a jour en gardant id, date, favori et notes`() = runBlocking {
        keys()
        plantNet(oak)
        aiSuccess(analysis())

        val outcome = repository.reanalyzeAndUpdate(storedFiche()) as IdentificationOutcome.Success

        assertEquals(7L, outcome.id)
        val updated = updates().single()
        assertEquals(7L, updated.id)
        assertEquals(1_700_000_000_000, updated.dateTime)
        assertTrue(updated.isFavorite)
        assertEquals("près du ruisseau", updated.notes)
        assertEquals("GEMINI", updated.aiProvider)
        // Une validation manuelle antérieure est remplacée par la nouvelle analyse.
        assertFalse(updated.userConfirmed)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `une relance hors ligne n'est jamais mise en file`() = runBlocking {
        keys()
        plantNet(error = PlantNetError.NETWORK)
        aiFailed(AiFailureReason.NETWORK)

        val outcome = repository.reanalyzeAndUpdate(storedFiche()) as IdentificationOutcome.Failure

        assertFalse(outcome.queuedForRetry)
        assertEquals(strings.get(R.string.id_reanalyze_offline), outcome.message)
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `la photo complementaire est enregistree avant l'analyse, avec son organe`() = runBlocking {
        keys()
        plantNet(oak)
        aiSuccess(analysis())

        repository.addPhotoAndReanalyze(storedFiche(), "/p/ecorce.jpg", PhotoOrgan.BARK)

        coVerifyOrder {
            dao.update(match { it.photoPaths == listOf("/p/1.jpg", "/p/ecorce.jpg") && it.aiProvider == "NONE" })
            plantNetClient.identify(any(), listOf("habit", "bark"), any(), any())
            dao.update(match { it.aiProvider == "GEMINI" })
        }
    }

    @Test
    fun `si l'analyse echoue la photo complementaire reste sur la fiche`() = runBlocking {
        keys(plantNet = false)
        aiFailed(AiFailureReason.QUOTA)

        repository.addPhotoAndReanalyze(storedFiche(), "/p/ecorce.jpg", PhotoOrgan.BARK)

        assertEquals(listOf("/p/1.jpg", "/p/ecorce.jpg"), updates().single().photoPaths)
        verify(exactly = 0) { imageStorage.delete(any()) }
    }

    // --- Fiche par nom ---

    private fun webPhoto() {
        coEvery { webPhotos.findPhoto(any(), any()) } returns WebPhotoClient.WebPhoto(byteArrayOf(1), "Chêne")
        coEvery { imageStorage.saveFromBytes(any()) } returns "/p/web.jpg"
    }

    @Test
    fun `fiche par nom - ni lieu, ni sante, ni photo a demander`() = runBlocking {
        keys()
        webPhoto()
        plantNet(oak)
        aiSuccess(analysis())

        val outcome = repository.identifyByName("chêne") as IdentificationOutcome.Success

        assertEquals("chêne", outcome.result.queriedName)
        assertNull(outcome.result.health)
        assertTrue(outcome.result.photoSuggestions.isEmpty())
        val entity = inserted()
        assertEquals("chêne", entity.queriedName)
        assertEquals(listOf("/p/web.jpg"), entity.photoPaths)
        assertNull(entity.latitude)
        val input = slot<AiAnalysisInput>()
        coVerify { orchestrator.analyze(capture(input)) }
        assertEquals("chêne", input.captured.queriedName)
        assertNull(input.captured.gps)
    }

    @Test
    fun `fiche par nom - aucune photo sur le web`() = runBlocking {
        keys()
        coEvery { webPhotos.findPhoto(any(), any()) } returns null

        val outcome = repository.identifyByName("zzz") as IdentificationOutcome.Failure

        assertEquals(FailureKind.NO_PHOTO, outcome.kind)
        assertEquals(strings.get(R.string.name_no_photo, "zzz"), outcome.message)
        coVerify(exactly = 0) { orchestrator.analyze(any()) }
    }

    @Test
    fun `fiche par nom - hors ligne des la recherche de photo`() = runBlocking {
        keys()
        coEvery { webPhotos.findPhoto(any(), any()) } throws IOException("hors ligne")

        val outcome = repository.identifyByName("chêne") as IdentificationOutcome.Failure

        assertEquals(FailureKind.NETWORK, outcome.kind)
        assertEquals(strings.get(R.string.name_offline), outcome.message)
        assertFalse(outcome.queuedForRetry)
    }

    @Test
    fun `fiche par nom - un nom qui ne designe rien n'est pas enregistre et sa photo est supprimee`() = runBlocking {
        keys(plantNet = false)
        webPhoto()
        aiSuccess(analysis(scientific = "", confidence = 0))

        val outcome = repository.identifyByName("tournevis") as IdentificationOutcome.Failure

        assertEquals(FailureKind.NO_RESULT, outcome.kind)
        assertEquals(strings.get(R.string.name_unknown, "tournevis"), outcome.message)
        coVerify(exactly = 0) { dao.insert(any()) }
        verify { imageStorage.delete("/p/web.jpg") }
    }

    // --- Défauts connus (audit du 2026-11-10) ---

    @Test
    fun `DEFAUT CONNU F2 - une espece toxique listee sort sans mention en resultat Pl@ntNet brut`() = runBlocking {
        keys(ai = false)
        plantNet(SpeciesCandidate("Colchicum autumnale", "Colchique d'automne", 90))
        ai(AiOutcome.NoProvidersConfigured)

        repository.identifyAndSave(request)

        // Colchicum figure dans ToxicSpeciesChecker, qui n'est consulté que pour les alternatives.
        // Attendu après correction : voir la décision prise sur F2 dans le rapport d'audit.
        assertNull(inserted().toxic)
    }

    @Test
    fun `DEFAUT CONNU F2 - l'IA peut declarer non toxique une espece listee`() = runBlocking {
        keys(plantNet = false)
        aiSuccess(analysis(scientific = "Colchicum autumnale", toxic = false))

        repository.identifyAndSave(request)

        assertEquals(false, inserted().toxic)
    }

    @Test
    fun `fiche par nom et IA injoignable - aucun reessai promis, photo supprimee`() = runBlocking {
        keys(plantNet = false)
        webPhoto()
        aiFailed(AiFailureReason.NETWORK)

        val outcome = repository.identifyByName("chêne") as IdentificationOutcome.Failure

        assertEquals(FailureKind.NETWORK, outcome.kind)
        assertFalse(outcome.queuedForRetry)
        assertEquals(strings.get(R.string.name_ai_offline), outcome.message)
        verify { imageStorage.delete("/p/web.jpg") }
    }

    @Test
    fun `un favori ou une note modifies pendant la relance ne sont pas ecrases`() = runBlocking {
        keys()
        plantNet(oak)
        aiSuccess(analysis())
        val atStart = storedFiche()
        // Pendant l'analyse, l'utilisateur a retiré le favori et réécrit sa note.
        coEvery { dao.getById(7) } returns atStart.copy(isFavorite = false, notes = "note récente")

        repository.reanalyzeAndUpdate(atStart)

        val updated = updates().single()
        assertFalse(updated.isFavorite)
        assertEquals("note récente", updated.notes)
    }
}
