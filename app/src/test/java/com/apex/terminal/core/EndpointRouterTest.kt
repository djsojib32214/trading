package com.apex.terminal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointRouterTest {

    private var now = 1_000_000L
    private val router = EndpointRouter(clock = { now })

    @Test
    fun `starts on the primary hosts`() {
        assertEquals(EndpointRouter.Route.PRIMARY, router.restRoute)
        assertEquals(EndpointRouter.Route.PRIMARY, router.wsRoute)
        assertEquals(Endpoints.REST_PRIMARY, router.restBase())
        assertEquals(Endpoints.WS_PRIMARY, router.wsBase())
        assertFalse(router.snapshot.anyOnMirror)
    }

    @Test
    fun `a failed primary request flips rest to the mirror`() {
        router.onRestResult(EndpointRouter.Route.PRIMARY, success = false)

        assertEquals(EndpointRouter.Route.MIRROR, router.restRoute)
        assertEquals(Endpoints.REST_MIRROR, router.restBase())
        assertTrue(router.snapshot.restOnMirror)
        assertEquals(1L, router.restSwitchCount())
    }

    @Test
    fun `repeated failures do not flip-flop back and forth`() {
        router.onRestResult(EndpointRouter.Route.PRIMARY, success = false)
        router.onRestResult(EndpointRouter.Route.MIRROR, success = false)
        router.onRestResult(EndpointRouter.Route.MIRROR, success = false)

        assertEquals(EndpointRouter.Route.MIRROR, router.restRoute)
        assertEquals(1L, router.restSwitchCount())
    }

    @Test
    fun `a retried primary that succeeds promotes back to primary`() {
        router.failoverRest()
        assertEquals(EndpointRouter.Route.MIRROR, router.restRoute)

        router.onRestResult(EndpointRouter.Route.PRIMARY, success = true)
        assertEquals(EndpointRouter.Route.PRIMARY, router.restRoute)
        assertFalse(router.snapshot.restOnMirror)
    }

    @Test
    fun `primary re-probe is only due after the retry interval`() {
        router.failoverRest()
        assertFalse(router.dueForPrimaryRestProbe())

        now += EndpointRouter.DEFAULT_PRIMARY_RETRY_INTERVAL_MS - 1
        assertFalse(router.dueForPrimaryRestProbe())

        now += 1
        assertTrue(router.dueForPrimaryRestProbe())
    }

    @Test
    fun `websocket failover is independent from rest`() {
        router.failoverWs()

        assertEquals(EndpointRouter.Route.MIRROR, router.wsRoute)
        assertEquals(EndpointRouter.Route.PRIMARY, router.restRoute)
        assertEquals(Endpoints.WS_MIRROR, router.wsBase())
        assertTrue(router.snapshot.wsOnMirror)
        assertFalse(router.snapshot.restOnMirror)
        assertTrue(router.snapshot.anyOnMirror)
    }

    @Test
    fun `websocket promotes back to primary when the mirror probe is due`() {
        router.failoverWs()
        assertFalse(router.dueForPrimaryWsProbe())
        now += EndpointRouter.DEFAULT_PRIMARY_RETRY_INTERVAL_MS
        assertTrue(router.dueForPrimaryWsProbe())

        router.promoteWs()
        assertEquals(EndpointRouter.Route.PRIMARY, router.wsRoute)
        assertFalse(router.snapshot.wsOnMirror)
    }

    @Test
    fun `ws connect on primary clears the switch timestamp`() {
        router.failoverWs()
        now += 5_000L
        router.promoteWs()
        router.onWsConnected()
        assertFalse(router.dueForPrimaryWsProbe())
    }
}
