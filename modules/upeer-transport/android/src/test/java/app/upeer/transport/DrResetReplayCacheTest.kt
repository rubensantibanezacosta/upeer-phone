package app.upeer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrResetReplayCacheTest {
  private val resetId = "123e4567-e89b-12d3-a456-426614174000"

  @Test
  fun acceptsEachResetIdOncePerPeer() {
    val cache = DrResetReplayCache { 1_000L }

    assertTrue(cache.accept("peer-a", resetId))
    assertFalse(cache.accept("peer-a", resetId))
    assertTrue(cache.accept("peer-b", resetId))
  }

  @Test
  fun acceptsResetIdAgainAfterTtl() {
    var now = 1_000L
    val cache = DrResetReplayCache { now }

    assertTrue(cache.accept("peer-a", resetId))
    now += 24 * 60 * 60 * 1000L + 1
    assertTrue(cache.accept("peer-a", resetId))
  }

  @Test
  fun rejectsInvalidResetId() {
    val cache = DrResetReplayCache { 1_000L }

    assertFalse(cache.accept("peer-a", "message-1"))
  }
}
