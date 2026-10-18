package com.apex.terminal.core

/**
 * Official Binance production endpoints plus the `binance.vision` public
 * market-data mirrors used when a regional ISP (e.g. Bangladesh DNS filtering)
 * cannot resolve or reach the primary hosts.
 */
object Endpoints {

    const val REST_PRIMARY: String = "https://api.binance.com"
    const val REST_MIRROR: String = "https://data-api.binance.vision"

    const val WS_PRIMARY: String = "wss://stream.binance.com:9443/ws/"
    const val WS_MIRROR: String = "wss://data-stream.binance.vision/ws/"

    const val KLINES_PATH: String = "/api/v3/klines"
    const val SERVER_TIME_PATH: String = "/api/v3/time"
    const val TICKER_24H_PATH: String = "/api/v3/ticker/24hr"

    /** Public logo CDN used by the symbol search sheet. */
    const val LOGO_BASE: String = "https://bin.bnbstatic.com/static/assets/logos/"
}

/** Immutable snapshot of the active routing decision, surfaced to the HUD. */
data class RouteSnapshot(
    val restOnMirror: Boolean,
    val wsOnMirror: Boolean,
) {
    val anyOnMirror: Boolean get() = restOnMirror || wsOnMirror
}
