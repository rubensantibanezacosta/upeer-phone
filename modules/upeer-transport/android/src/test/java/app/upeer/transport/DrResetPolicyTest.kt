package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrResetPolicyTest {
  private val currentKey = "bb".repeat(32)
  private val otherKey = "cc".repeat(32)

  @Test
  fun acceptsSameSignedPreKeyAsAnIdempotentReset() {
    assertEquals(
      SignedPreKeyUpdate.SAME,
      DrResetPolicy.classifySignedPreKey(9, currentKey, 9, currentKey),
    )
  }

  @Test
  fun classifiesSignedPreKeyChanges() {
    assertEquals(SignedPreKeyUpdate.UPDATED, DrResetPolicy.classifySignedPreKey(9, currentKey, 10, otherKey))
    assertEquals(SignedPreKeyUpdate.UPDATED, DrResetPolicy.classifySignedPreKey(0, "", 10, otherKey))
    assertEquals(SignedPreKeyUpdate.STALE, DrResetPolicy.classifySignedPreKey(9, currentKey, 8, otherKey))
    assertEquals(SignedPreKeyUpdate.INVALID, DrResetPolicy.classifySignedPreKey(9, currentKey, 9, otherKey))
  }

  @Test
  fun validatesResetIds() {
    assertTrue(DrResetPolicy.validResetId("123e4567-e89b-12d3-a456-426614174000"))
    assertTrue(DrResetPolicy.validResetId("123E4567-E89B-12D3-A456-426614174000"))
    assertFalse(DrResetPolicy.validResetId("message-1"))
    assertFalse(DrResetPolicy.validResetId(""))
  }
}
