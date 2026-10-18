package com.apex.terminal.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Bangladesh ISP failover engine.
 *
 * The router is intentionally **sticky**: once a request fails on the primary
 * host we immediately fail over to the `binance.vision` mirror and keep using
 * it, because re-trying the blocked host on every request adds a full DNS +
 * connect timeout to each call (2-8s on restricted mobile data). A single
 * primary re-probe is scheduled after [primaryRetryIntervalMs] so the app
 * automatically recovers when the ISP routing heals.
 *
 * All state is optimistic and thread-safe so it can be shared between the
 * OkHttp dispatcher threads, the WebSocket reader thread and the UI.
 */
class EndpointRouter(
    private val primaryRest: String = Endpoints.REST_PRIMARY,
    private val mirrorRest: String = Endpoints.REST_MIRROR,
    private val primaryWs: String = Endpoints.WS_PRIMARY,
    private val mirrorWs: String = Endpoints.WS_MIRROR,
    private val primaryRetryIntervalMs: Long = DEFAULT_PRIMARY_RETRY_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    enum class Route { PRIMARY, MIRROR }

    private val restIsPrimary = AtomicBoolean(true)
    private val wsIsPrimary = AtomicBoolean(true)

    private val restSwitchedAt = AtomicLong(0L)
    private val wsSwitchedAt = AtomicLong(0L)

    private val restFailovers = AtomicLong(0L)
    private val wsFailovers = AtomicLong(0L)

    val restRoute: Route get() = if (restIsPrimary.get()) Route.PRIMARY else Route.MIRROR
    val wsRoute: Route get() = if (wsIsPrimary.get()) Route.PRIMARY else Route.MIRROR

    val snapshot: RouteSnapshot
        get() = RouteSnapshot(restOnMirror = !restIsPrimary.get(), wsOnMirror = !wsIsPrimary.get())

    fun restBase(): String = if (restIsPrimary.get()) primaryRest else mirrorRest

    fun wsBase(): String = if (wsIsPrimary.get()) primaryWs else mirrorWs

    fun restSwitchCount(): Long = restFailovers.get()

    fun wsSwitchCount(): Long = wsFailovers.get()

    // ---------------------------------------------------------------- REST --

    /** Reports the outcome of a REST call that was issued against [route]. */
    fun onRestResult(route: Route, success: Boolean) {
        if (success) {
            if (route == Route.PRIMARY) promoteRest()
        } else {
            failoverRest()
        }
    }

    fun failoverRest(): Boolean {
        if (restIsPrimary.compareAndSet(true, false)) {
            restSwitchedAt.set(clock())
            restFailovers.incrementAndGet()
            return true
        }
        return false
    }

    fun promoteRest(): Boolean {
        if (restIsPrimary.compareAndSet(false, true)) {
            restSwitchedAt.set(0L)
            return true
        }
        return false
    }

    /**
     * `true` when we are running on the mirror and enough time has passed to
     * justify a single low-cost probe against the primary host.
     */
    fun dueForPrimaryRestProbe(): Boolean =
        !restIsPrimary.get() && clock() - restSwitchedAt.get() >= primaryRetryIntervalMs

    // ----------------------------------------------------------- WebSocket --

    fun onWsConnected() {
        if (wsIsPrimary.get()) wsSwitchedAt.set(0L)
    }

    fun failoverWs(): Boolean {
        if (wsIsPrimary.compareAndSet(true, false)) {
            wsSwitchedAt.set(clock())
            wsFailovers.incrementAndGet()
            return true
        }
        return false
    }

    fun promoteWs(): Boolean {
        if (wsIsPrimary.compareAndSet(false, true)) {
            wsSwitchedAt.set(0L)
            return true
        }
        return false
    }

    fun dueForPrimaryWsProbe(): Boolean =
        !wsIsPrimary.get() && clock() - wsSwitchedAt.get() >= primaryRetryIntervalMs

    companion object {
        const val DEFAULT_PRIMARY_RETRY_INTERVAL_MS: Long = 10 * 60 * 1000L
    }
}
