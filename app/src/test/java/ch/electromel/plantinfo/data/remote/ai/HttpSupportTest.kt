package ch.electromel.plantinfo.data.remote.ai

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import okhttp3.Call
import okhttp3.Callback
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpSupportTest {

    @Test
    fun `un code HTTP est traduit en motif typé`() {
        assertEquals(AiFailureReason.BILLING, HttpSupport.reasonForStatus(402))
        assertEquals(AiFailureReason.BILLING, HttpSupport.reasonForStatus(403, "not enough credits"))
        assertEquals(AiFailureReason.INVALID_KEY, HttpSupport.reasonForStatus(403))
        assertEquals(AiFailureReason.INVALID_KEY, HttpSupport.reasonForStatus(401))
        assertEquals(AiFailureReason.BILLING, HttpSupport.reasonForStatus(429, "insufficient_quota"))
        assertEquals(AiFailureReason.QUOTA, HttpSupport.reasonForStatus(429))
        assertEquals(AiFailureReason.SERVER, HttpSupport.reasonForStatus(503))
        assertEquals(AiFailureReason.UNKNOWN, HttpSupport.reasonForStatus(418))
    }

    @Test
    fun `annuler la coroutine annule l'appel reseau`() = runTest {
        val call = mockk<Call>(relaxed = true)
        val callback = slot<Callback>()
        every { call.enqueue(capture(callback)) } returns Unit

        val job = launch { call.awaitResponse() }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()

        verify(exactly = 1) { call.cancel() }
    }
}
