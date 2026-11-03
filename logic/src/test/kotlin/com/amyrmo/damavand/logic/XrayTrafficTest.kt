package com.amyrmo.damavand.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Xray traffic-counter parsing: the `tag,dir,bytes;…` wire format. */
class XrayTrafficTest {

    @Test
    fun `proxy counters are the session total`() {
        val (down, up) = XrayTraffic.trafficDeltaOf(
            "proxy,downlink,1000;proxy,uplink,2000;direct,downlink,50;direct,uplink,60",
        )
        assertEquals(1000L, down)
        assertEquals(2000L, up)
    }

    @Test
    fun `auto-select members count towards the total`() {
        val (down, up) = XrayTraffic.trafficDeltaOf(
            "proxy@0,downlink,300;proxy@0,uplink,400;proxy@12,downlink,7;proxy@12,uplink,8",
        )
        assertEquals(307L, down)
        assertEquals(408L, up)
    }

    @Test
    fun `busiest outbound is the fallback when proxy reports nothing`() {
        val (down, up) = XrayTraffic.trafficDeltaOf(
            "proxy,downlink,0;proxy,uplink,0;warp,downlink,900;warp,uplink,100;other,downlink,5;other,uplink,5",
        )
        assertEquals(900L, down)
        assertEquals(100L, up)
    }

    @Test
    fun `infrastructure tags are never meters`() {
        // Only infra tags moved: byTag stays empty, so no fallback fires.
        val result = XrayTraffic.trafficDeltaOf("direct,downlink,500;dns-out,uplink,700;fragment,downlink,1")
        assertEquals(0L to 0L, result)
    }

    @Test
    fun `garbage input is zero`() {
        assertEquals(0L to 0L, XrayTraffic.trafficDeltaOf(null))
        assertEquals(0L to 0L, XrayTraffic.trafficDeltaOf(""))
        assertEquals(0L to 0L, XrayTraffic.trafficDeltaOf("nonsense;proxy,downlink,notanumber"))
    }

    @Test
    fun `member tag recognition`() {
        assertEquals(true, XrayTraffic.isAutoSelectMember("proxy@0"))
        assertEquals(true, XrayTraffic.isAutoSelectMember("proxy@123"))
        assertEquals(false, XrayTraffic.isAutoSelectMember("proxy"))
        assertEquals(false, XrayTraffic.isAutoSelectMember("proxy@"))
        assertEquals(false, XrayTraffic.isAutoSelectMember("proxy@1a"))
    }
}
