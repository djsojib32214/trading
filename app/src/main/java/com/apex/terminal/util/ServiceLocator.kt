package com.apex.terminal.util

import android.content.Context
import com.apex.terminal.core.EndpointRouter
import com.apex.terminal.core.Json
import com.apex.terminal.data.local.AppDatabase
import com.apex.terminal.data.prefs.SettingsStore
import com.apex.terminal.data.remote.BinanceApi
import com.apex.terminal.data.remote.BinanceRestClient
import com.apex.terminal.data.remote.BinanceStreamClient
import com.apex.terminal.data.repository.MarketRepository
import com.apex.terminal.data.repository.SymbolCatalog
import com.apex.terminal.service.MonitorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled service locator.
 *
 * The dependency graph is small enough that a container would be ceremony:
 * one OkHttp pool, one Room database, one repository — all owned by the
 * process and shared between the UI and the foreground monitor service.
 */
class ServiceLocator private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val endpointRouter: EndpointRouter = EndpointRouter()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        // Tight timeouts: a DNS-blackholed host must fail over fast on 3G.
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .pingInterval(0, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.binance.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(Json.gson))
        .build()

    val binanceApi: BinanceApi = retrofit.create(BinanceApi::class.java)

    val restClient: BinanceRestClient = BinanceRestClient(binanceApi, endpointRouter)

    val streamClient: BinanceStreamClient =
        BinanceStreamClient(okHttpClient, Json.gson, endpointRouter)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val marketRepository: MarketRepository by lazy {
        MarketRepository(
            dao = database.candleDao(),
            rest = restClient,
            stream = streamClient,
            router = endpointRouter,
            scope = applicationScope,
        )
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val symbolCatalog: SymbolCatalog by lazy { SymbolCatalog(appContext, Json.gson) }

    val monitorController: MonitorController by lazy { MonitorController(appContext) }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun init(context: Context): ServiceLocator = instance ?: synchronized(this) {
            instance ?: ServiceLocator(context).also { instance = it }
        }

        fun get(): ServiceLocator = instance
            ?: error("ServiceLocator.init() must be called from Application.onCreate()")
    }
}
