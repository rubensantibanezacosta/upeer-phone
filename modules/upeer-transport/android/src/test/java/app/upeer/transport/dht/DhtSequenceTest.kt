package app.upeer.transport.dht

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtSequenceTest {
  @Test
  fun rejectsRollbackAndIdenticalSequenceIsAccepted() {
    assertFalse(DhtSequence.validate(10L, 9L).valid)
    assertEquals("Sequence rollback detected", DhtSequence.validate(10L, 9L).reason)
    val identical = DhtSequence.validate(10L, 10L)
    assertTrue(identical.valid)
    assertFalse(identical.requiresPoW)
  }

  @Test
  fun acceptsBootstrapAndBoundedJumps() {
    assertTrue(DhtSequence.validate(0L, DhtSequence.MAX_JUMP).valid)
    assertTrue(DhtSequence.validate(1L, 2L).valid)
    assertTrue(DhtSequence.validate(1L, 1L + DhtSequence.MAX_JUMP).valid)
  }

  @Test
  fun requiresPoWForLargeJumps() {
    val jump = DhtSequence.validate(1L, 1L + DhtSequence.MAX_JUMP + 1)
    assertFalse(jump.valid)
    assertTrue(jump.requiresPoW)
  }

  @Test
  fun incrementsOnlyWhenAddressChanges() {
    assertEquals(4L, DhtSequence.next(4L, "200::1", "200::1"))
    assertEquals(5L, DhtSequence.next(4L, "200::1", "200::2"))
    assertEquals(1L, DhtSequence.next(0L, "", "200::1"))
  }

  @Test
  fun rejectsSequenceOverflowWhenTheAddressChanges() {
    assertEquals(Long.MAX_VALUE, DhtSequence.next(Long.MAX_VALUE, "200::1", "200::1"))
    assertTrue(runCatching { DhtSequence.next(Long.MAX_VALUE, "200::1", "200::2") }.isFailure)
  }
}
