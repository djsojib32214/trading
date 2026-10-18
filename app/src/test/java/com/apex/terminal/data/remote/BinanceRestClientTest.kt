package com.apex.terminal.data.remote

import com.apex.terminal.core.EndpointRouter
import com.apex.terminal.core.Json
import com.apex.terminal.data.remote.dto.KlineMapper
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class BinanceRestClientTest {

    private lateinit var primary: MockWebServer
    private lateinit var mirror: MockWebServer

    private val klinePayload = """
        [
          [1700000000000,"1.0","2.0","0.5","1.5","100.0",1700000059999,"150.0",10,"50.0","75.0","0"],
          [1700000060000,"1.5","3.0","1.0","2.5","200.0",1700000119999,"400.0",20,"100.0","200.0","0"]
        ]
    """.trimIndent()

    @Before
    fun setUp() {
        primary = MockWebServer().apply { start() }
        mirror = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        runCatching { primary.shutdown() }
        runCatching { mirror.shutdown() }
    }

    private fun client(router: EndpointRouter): BinanceRestClient {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://api.binance.invalid/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create(Json.gson))
            .build()
            .create(BinanceApi::class.java)
        return BinanceRestClient(api, router, callTimeoutMs = 4_000L)
    }

    private fun router() = EndpointRouter(
        primaryRest = primary.url("/").toString().trimEnd('/'),
        mirrorRest = mirror.url("/").toString().trimEnd('/'),
        primaryWs = "ws://primary.invalid/ws/",
        mirrorWs = "ws://mirror.invalid/ws/",
    )

    @Test
    fun `primary host is used when it responds`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(200).setBody(klinePayload))
        val router = router()
        val client = client(router)

        val candles = client.klines("BTCUSDT", "1m", limit = 2)

        assertEquals(2, candles.size)
        assertEquals(1_700_000_000_000L, candles[0].time)
        assertEquals(1.5, candles[0].close, 1e-9)
        assertEquals(EndpointRouter.Route.PRIMARY, router.restRoute)
        assertEquals(1, primary.requestCount)
        assertEquals(0, mirror.requestCount)

        val recorded = primary.takeRequest()
        assertTrue(recorded.path!!.contains("/api/v3/klines"))
        assertTrue(recorded.path!!.contains("symbol=BTCUSDT"))
        assertTrue(recorded.path!!.contains("interval=1m"))
    }

    @Test
    fun `rest fails over to the binance vision mirror when the primary 5xx`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        mirror.enqueue(MockResponse().setResponseCode(200).setBody(klinePayload))

        val router = router()
        val client = client(router)

        val candles = client.klines("ETHUSDT", "5m", limit = 2)

        assertEquals(2, candles.size)
        assertEquals(EndpointRouter.Route.MIRROR, router.restRoute)
        assertEquals(1L, router.restSwitchCount())
        assertEquals(1, mirror.requestCount)
        assertTrue(router.snapshot.restOnMirror)
    }

    @Test
    fun `once failed over every subsequent call goes straight to the mirror`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(500))
        mirror.enqueue(MockResponse().setResponseCode(200).setBody(klinePayload))
        mirror.enqueue(MockResponse().setResponseCode(200).setBody(klinePayload))

        val router = router()
        val client = client(router)

        client.klines("BTCUSDT", "1m")
        client.klines("BTCUSDT", "1m")

        assertEquals(1, primary.requestCount)
        assertEquals(2, mirror.requestCount)
    }

    @Test
    fun `both hosts failing surfaces the error`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(502))
        mirror.enqueue(MockResponse().setResponseCode(502))

        val client = client(router())
        var failed = false
        try {
            client.klines("BTCUSDT", "1m")
        } catch (t: Throwable) {
            failed = true
        }
        assertTrue("expected the request to fail", failed)
    }

    @Test
    fun `backfill walks forward from the last stored timestamp plus one`() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(200).setBody(klinePayload))
        val client = client(router())

        val collected = client.backfill(
            symbol = "BTCUSDT",
            interval = "1m",
            latestTimestamp = 1_699_999_999_999L,
            pageSize = 1000,
            intervalMillis = 60_000L,
        )

        assertEquals(2, collected.size)
        val request = primary.takeRequest()
        assertTrue(
            "startTime must be latestTimestamp + 1, got ${request.path}",
            request.path!!.contains("startTime=1700000000000"),
        )
    }

    @Test
    fun `backfill stops immediately when the store is already caught up`() = runBlocking {
        val client = client(router())
        val collected = client.backfill(
            symbol = "BTCUSDT",
            interval = "1m",
            latestTimestamp = System.currentTimeMillis(),
            intervalMillis = 60_000L,
        )
        assertTrue(collected.isEmpty())
        assertEquals(0, primary.requestCount)
    }

    @Test
    fun `ticker 24h response is mapped to doubles`() = runBlocking {
        primary.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"symbol":"BTCUSDT","lastPrice":"68123.40","priceChangePercent":"-0.64",
                     "highPrice":"69000.00","lowPrice":"67000.00","quoteVolume":"1234567.89"}]""",
            ),
        )
        val client = client(router())
        val tickers = client.ticker24h(listOf("BTCUSDT"))

        assertEquals(1, tickers.size)
        assertEquals(68123.40, tickers[0].lastPrice, 1e-6)
        assertEquals(-0.64, tickers[0].priceChangePercent, 1e-9)
        assertEquals(1234567.89, tickers[0].quoteVolume, 1e-6)
    }

    @Test
    fun `kline mapper tolerates malformed rows`() {
        val good = JsonParser.parseString("""[1,"2","3","1","2.5","10"]""").asJsonArray
        val short = JsonParser.parseString("""[1,"2"]""").asJsonArray

        val candle = KlineMapper.toCandle(good.toList())
        assertNotNull(candle)
        assertEquals(1L, candle!!.time)
        assertEquals(2.5, candle.close, 1e-9)

        assertNull(KlineMapper.toCandle(short.toList()))
    }
}
