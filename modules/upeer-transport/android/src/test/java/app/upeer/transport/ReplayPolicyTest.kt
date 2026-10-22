package app.upeer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPolicyTest {
  @Test
  fun acceptsUuidMessageIds() {
    assertTrue(ReplayPolicy.validMessageID("123e4567-e89b-12d3-a456-426614174000"))
    assertTrue(ReplayPolicy.validMessageID("123E4567-E89B-12D3-A456-426614174000"))
  }

  @Test
  fun rejectsNonUuidMessageIds() {
    assertFalse(ReplayPolicy.validMessageID(""))
    assertFalse(ReplayPolicy.validMessageID("message-1"))
    assertFalse(ReplayPolicy.validMessageID("123e4567e89b12d3a456426614174000"))
    assertFalse(ReplayPolicy.validMessageID("123e4567-e89b-12d3-a456-42661417400z"))
  }

  @Test
  fun onlyAcceptsAcksForOwnMessagesFromTheirPeer() {
    assertTrue(ReplayPolicy.validAck("peer-a", "peer-a", true, "delivered"))
    assertTrue(ReplayPolicy.validAck("peer-a", "peer-a", true, "read"))
    assertFalse(ReplayPolicy.validAck("peer-b", "peer-a", true, "delivered"))
    assertFalse(ReplayPolicy.validAck("peer-a", "peer-a", false, "delivered"))
    assertFalse(ReplayPolicy.validAck("peer-a", "peer-a", true, "failed"))
  }
}
