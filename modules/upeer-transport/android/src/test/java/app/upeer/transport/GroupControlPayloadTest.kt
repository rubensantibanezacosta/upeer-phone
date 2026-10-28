package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupControlPayloadTest {
  private val member = "a1".repeat(32)
  private val otherMember = "b2".repeat(32)
  private val publicKey = "c3".repeat(32)
  private val senderKey = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"

  @Test
  fun buildsAnInviteThatTheReceivePolicyAccepts() {
    val serialized = GroupControlPayload.invite(
      name = "Equipo",
      members = listOf(member, otherMember),
      memberPublicKeys = mapOf(member to publicKey),
      epoch = 3,
      senderKey = senderKey,
      avatar = "data:image/webp;base64,AAAA",
    )

    assertNotNull(serialized)
    val parsed = GroupPayloadPolicy.parseInvite(serialized!!)
    assertNotNull(parsed)
    assertEquals("Equipo", parsed!!.name)
    assertEquals(listOf(member, otherMember), parsed.members)
    assertEquals(publicKey, parsed.memberPublicKeys[member])
    assertEquals(3, parsed.epoch)
    assertEquals(senderKey, parsed.senderKey)
    assertEquals("data:image/webp;base64,AAAA", parsed.avatar)
  }

  @Test
  fun normalizesTheInviteSenderKeyToLowercase() {
    val serialized = GroupControlPayload.invite("Equipo", listOf(member), emptyMap(), 1, senderKey.uppercase(), null)

    val payload = JSONObject(serialized!!)
    assertEquals(senderKey, payload.optString("senderKey"))
  }

  @Test
  fun omitsTheAvatarWhenThereIsNone() {
    val serialized = GroupControlPayload.invite("Equipo", listOf(member), emptyMap(), 1, senderKey, null)

    assertTrue(!JSONObject(serialized!!).has("avatar"))
  }

  @Test
  fun rejectsInvitesWithoutNameOrMembers() {
    assertNull(GroupControlPayload.invite("", listOf(member), emptyMap(), 1, senderKey, null))
    assertNull(GroupControlPayload.invite("Equipo", emptyList(), emptyMap(), 1, senderKey, null))
    assertNull(GroupControlPayload.invite("Equipo", listOf(member), emptyMap(), 0, senderKey, null))
    assertNull(GroupControlPayload.invite("Equipo", listOf(member), emptyMap(), 1, "not-a-key", null))
  }

  @Test
  fun buildsAMembershipUpdateThatTheReceivePolicyReads() {
    val serialized = GroupControlPayload.update(
      members = listOf(member, otherMember),
      memberPublicKeys = mapOf(otherMember to publicKey),
      epoch = 4,
      senderKey = senderKey,
    )

    val parsed = GroupPayloadPolicy.parseUpdate(serialized!!)
    assertNotNull(parsed)
    assertEquals(listOf(member, otherMember), parsed!!.members)
    assertEquals(publicKey, parsed.memberPublicKeys?.get(otherMember))
    assertEquals(4, parsed.epoch)
    assertEquals(senderKey, parsed.senderKey)
    assertNull(parsed.adminUpeerId)
  }

  @Test
  fun serializesAvatarRemovalAsNull() {
    val serialized = GroupControlPayload.update(name = "Equipo", removeAvatar = true)

    val payload = JSONObject(serialized!!)
    assertTrue(payload.has("avatar"))
    assertEquals(JSONObject.NULL, payload.get("avatar"))
    assertTrue(GroupPayloadPolicy.parseUpdate(serialized)!!.removeAvatar)
  }

  @Test
  fun transfersTheAdministrationWithoutTouchingTheSenderState() {
    val serialized = GroupControlPayload.update(adminUpeerId = otherMember)

    val parsed = GroupPayloadPolicy.parseUpdate(serialized!!)
    assertNotNull(parsed)
    assertEquals(otherMember, parsed!!.adminUpeerId)
    assertNull(parsed.epoch)
    assertNull(parsed.senderKey)
  }

  @Test
  fun rejectsEmptyOrInvalidUpdates() {
    assertNull(GroupControlPayload.update())
    assertNull(GroupControlPayload.update(name = ""))
    assertNull(GroupControlPayload.update(members = emptyList()))
    assertNull(GroupControlPayload.update(epoch = 0, senderKey = senderKey))
    assertNull(GroupControlPayload.update(epoch = 2, senderKey = "short"))
  }
}
