package app.upeer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundRateLimiterTest {
  @Test
  fun chatLimitRefillsAfterWindow() {
    var now = 0L
    val limiter = InboundRateLimiter { now }
    repeat(100) { assertTrue(limiter.allow("id:peer", "CHAT")) }
    assertFalse(limiter.allow("id:peer", "CHAT"))
    now += 60_000
    assertTrue(limiter.allow("id:peer", "CHAT"))
  }

  @Test
  fun limitsAreScopedBySourceAndType() {
    val limiter = InboundRateLimiter { 0L }
    repeat(100) { assertTrue(limiter.allow("id:peer-a", "CHAT")) }
    assertFalse(limiter.allow("id:peer-a", "CHAT"))
    assertTrue(limiter.allow("id:peer-b", "CHAT"))
    assertTrue(limiter.allow("id:peer-a", "ACK"))
  }

  @Test
  fun sealedLimitAppliesPerAddress() {
    val limiter = InboundRateLimiter { 0L }
    repeat(5_000) { assertTrue(limiter.allow("ip:200::1", "SEALED")) }
    assertFalse(limiter.allow("ip:200::1", "SEALED"))
    assertTrue(limiter.allow("ip:200::2", "SEALED"))
  }

  @Test
  fun unknownTypeAndBlankSourceAreUnlimited() {
    val limiter = InboundRateLimiter { 0L }
    repeat(1_000) { assertTrue(limiter.allow("id:peer", "UNKNOWN")) }
    repeat(1_000) { assertTrue(limiter.allow("", "CHAT")) }
  }

  @Test
  fun dhtLimitsMatchDesktopThresholds() {
    val limiter = InboundRateLimiter { 0L }
    repeat(20) { assertTrue(limiter.allow("ip:200::1", "DHT_QUERY")) }
    assertFalse(limiter.allow("ip:200::1", "DHT_QUERY"))
    repeat(10) { assertTrue(limiter.allow("ip:200::1", "DHT_STORE")) }
    assertFalse(limiter.allow("ip:200::1", "DHT_STORE"))
    assertTrue(limiter.allow("ip:200::1", "DHT_PING"))
  }

  @Test
  fun countsLimitedFrames() {
    val limiter = InboundRateLimiter { 0L }
    repeat(20) { assertTrue(limiter.allow("id:peer", "DR_RESET")) }
    assertFalse(limiter.allow("id:peer", "DR_RESET"))
    assertFalse(limiter.allow("id:peer", "DR_RESET"))
    assertTrue(limiter.limitedCount() == 2L)
  }
}
