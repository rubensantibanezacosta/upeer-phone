package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMessagePacketTest {
  private val groupId = "grp-8f14e45fceea167a"
  private val messageID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
  private val publicKey = "b".repeat(64)
  private val sealed = JSONObject()
    .put("nonce", "c".repeat(48))
    .put("ciphertext", "d".repeat(80))

  private fun packet(replyTo: String = "") = GroupMessagePacket.build(
    groupId = groupId,
    messageID = messageID,
    senderPublicKey = publicKey,
    timestamp = 1_700_000_000_000,
    sealed = sealed,
    epoch = 3,
    replyTo = replyTo,
  )

  @Test
  fun `builds a GROUP_MSG packet with the sealed content`() {
    val built = packet()
    assertEquals("GROUP_MSG", built.optString("type"))
    assertEquals(groupId, built.optString("groupId"))
    assertEquals(messageID, built.optString("id"))
    assertEquals(publicKey, built.optString("senderPublicKey"))
    assertEquals("c".repeat(48), built.optString("nonce"))
    assertEquals("d".repeat(80), built.optString("content"))
    assertEquals(3, built.optInt("epoch"))
    assertEquals(1_700_000_000_000L, built.optLong("timestamp"))
    assertFalse(built.has("replyTo"))
  }

  @Test
  fun `keeps a valid quoted message`() {
    assertTrue(packet(messageID).optString("replyTo") == messageID)
  }

  @Test
  fun `never includes the sender upeer id because the bridge signs it`() {
    assertFalse(packet().has("senderUpeerId"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a group id without prefix`() {
    GroupMessagePacket.build(groupId = "8f14e45f", messageID = messageID, senderPublicKey = publicKey, timestamp = 1, sealed = sealed, epoch = 0, replyTo = "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a message id that is not a uuid`() {
    GroupMessagePacket.build(groupId = groupId, messageID = "abc", senderPublicKey = publicKey, timestamp = 1, sealed = sealed, epoch = 0, replyTo = "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a sender public key of the wrong size`() {
    GroupMessagePacket.build(groupId = groupId, messageID = messageID, senderPublicKey = "ab", timestamp = 1, sealed = sealed, epoch = 0, replyTo = "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects an empty ciphertext`() {
    GroupMessagePacket.build(groupId = groupId, messageID = messageID, senderPublicKey = publicKey, timestamp = 1, sealed = JSONObject(), epoch = 0, replyTo = "")
  }

  @Test
  fun `recipients exclude the sender and deduplicate members`() {
    val self = "a".repeat(64)
    val other = "b".repeat(64)
    assertEquals(listOf(other), GroupMessagePacket.recipients(listOf(self, other, other.uppercase()), self))
  }

  @Test
  fun `recipients keep every member when the sender is not listed`() {
    val self = "a".repeat(64)
    val first = "b".repeat(64)
    val second = "c".repeat(64)
    assertEquals(listOf(first, second), GroupMessagePacket.recipients(listOf(first, second), self))
  }
}
