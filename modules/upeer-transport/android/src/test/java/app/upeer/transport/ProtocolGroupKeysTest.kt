package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolGroupKeysTest {
  private val accountA = "zz".repeat(32)
  private val groupId = "grp-0123456789abcdef"
  private val senderKey = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"

  private fun store(): ProtocolGroupKeys {
    val state = JSONObject()
    return ProtocolGroupKeys({ state }, Any(), {})
  }

  @Test
  fun storesAndReadsBackTheSenderKey() {
    val keys = store()

    assertTrue(keys.put(groupId, 3, senderKey))

    assertEquals(3, keys.epoch(groupId))
    assertEquals(senderKey, keys.senderKey(groupId))
    assertEquals(1, keys.count())
  }

  @Test
  fun normalizesTheSenderKeyToLowercase() {
    val keys = store()
    keys.put(groupId, 1, senderKey.uppercase())

    assertEquals(senderKey, keys.senderKey(groupId))
  }

  @Test
  fun matchesOnlyTheStoredEpoch() {
    val keys = store()
    keys.put(groupId, 4, senderKey)

    assertTrue(keys.matches(groupId, 4))
    assertEquals(false, keys.matches(groupId, 3))
    assertEquals(false, keys.matches("grp-ff", 4))
  }

  @Test
  fun rejectsInvalidInput() {
    val keys = store()

    assertEquals(false, keys.put("not-a-group", 1, senderKey))
    assertEquals(false, keys.put(groupId, 0, senderKey))
    assertEquals(false, keys.put(groupId, 1, "corta"))
    assertEquals(false, keys.put(groupId, 1, "zz".repeat(32)))
    assertEquals(0, keys.count())
  }

  @Test
  fun keepsGroupsIndependent() {
    val keys = store()
    val other = "grp-fedcba9876543210"
    keys.put(groupId, 1, senderKey)
    keys.put(other, 9, "11".repeat(32))

    assertEquals(1, keys.epoch(groupId))
    assertEquals(9, keys.epoch(other))
    assertEquals("11".repeat(32), keys.senderKey(other))
  }

  @Test
  fun forgetsOneGroupAtATime() {
    val keys = store()
    val other = "grp-fedcba9876543210"
    keys.put(groupId, 1, senderKey)
    keys.put(other, 1, senderKey)

    assertTrue(keys.forget(groupId))

    assertNull(keys.epoch(groupId))
    assertNull(keys.senderKey(groupId))
    assertNotNull(keys.epoch(other))
    assertEquals(false, keys.forget(groupId))
  }

  @Test
  fun clearRemovesEveryGroup() {
    val keys = store()
    keys.put(groupId, 1, senderKey)
    keys.put("grp-fedcba9876543210", 1, senderKey)
    keys.clear()

    assertEquals(0, keys.count())
    assertNull(keys.epoch(groupId))
  }

  @Test
  fun validatesEpochAndSenderKeyShapes() {
    assertTrue(ProtocolGroupKeys.validEpoch(1))
    assertEquals(false, ProtocolGroupKeys.validEpoch(0))
    assertEquals(false, ProtocolGroupKeys.validEpoch(-1))
    assertTrue(ProtocolGroupKeys.validSenderKey(senderKey))
    assertEquals(false, ProtocolGroupKeys.validSenderKey(accountA))
    assertEquals(false, ProtocolGroupKeys.validSenderKey("${senderKey}00"))
  }
}
