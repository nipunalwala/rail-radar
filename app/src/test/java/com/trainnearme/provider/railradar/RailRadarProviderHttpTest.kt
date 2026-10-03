package com.trainnearme.provider.railradar

import com.trainnearme.core.data.ProviderRateLimitedException
import com.trainnearme.core.data.ProviderUnauthorizedException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/** Runs the provider against a local web server, so the HTTP handling itself is exercised. */
class RailRadarProviderHttpTest {

    private val server = MockWebServer()
    private lateinit var provider: RailRadarProvider

    @Before
    fun setUp() {
        server.start()
        provider = RailRadarProvider(RailRadarProvider.createApi(OkHttpClient(), server.url("/").toString()))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/railradar/$name")).readText()

    private fun failure(block: suspend () -> Unit): Throwable = runBlocking {
        try {
            block()
            throw AssertionError("expected a failure")
        } catch (e: Exception) {
            e
        }
    }

    @Test
    fun `live board is requested from the documented path and parsed`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("live_DR.json")))

        val board = provider.liveBoard("DR", 2)

        assertEquals(47, board.size)
        assertEquals("/v1/stations/DR/live?hours=2", server.takeRequest().path)
    }

    @Test
    fun `timetable is requested from the documented path and parsed`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("timetable_DR.json")))

        val timetable = provider.timetable("DR")

        assertEquals(8, timetable.size)
        assertEquals("/v1/stations/DR/trains", server.takeRequest().path)
    }

    @Test
    fun `429 becomes a rate limit`() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"success":false}"""))

        assertTrue(failure { provider.liveBoard("DR", 2) } is ProviderRateLimitedException)
    }

    @Test
    fun `401 and 403 become a rejected key`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(403))

        assertTrue(failure { provider.liveBoard("DR", 2) } is ProviderUnauthorizedException)
        assertTrue(failure { provider.timetable("DR") } is ProviderUnauthorizedException)
    }

    @Test
    fun `other statuses and unsuccessful bodies fail without special meaning`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"success":false,"data":null}"""))

        assertTrue(failure { provider.liveBoard("DR", 2) } is HttpException)
        assertTrue(failure { provider.liveBoard("DR", 2) } is IOException)
    }
}
