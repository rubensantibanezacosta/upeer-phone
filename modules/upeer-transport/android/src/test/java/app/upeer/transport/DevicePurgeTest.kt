package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePurgeTest {
  private val peerID = "aa".repeat(32)
  private val otherPeer = "bb".repeat(32)
  private val revokedDevice = "cc".repeat(32)

  private fun state(): JSONObject = JSONObject()
    .put(
      "peers",
      JSONObject()
        .put(peerID, peer(revokedDevice))
        .put(otherPeer, peer("dd".repeat(32))),
    )
    .put("sessions", JSONObject().put(peerID, "{}").put(otherPeer, "{}"))

  private fun peer(deviceId: String): JSONObject = JSONObject()
    .put("publicKey", "ee".repeat(32))
    .put("devicePublicKey", "ff".repeat(32))
    .put("deviceId", deviceId)
    .put("deviceCertificate", JSONObject().put("deviceId", deviceId))
    .put("deviceSignedPreKey", JSONObject().put("spkId", 7L))

  @Test
  fun `removes the binding and the session of the revoked device`() {
    val state = state()
    assertTrue(DevicePurge.apply(state, revokedDevice))
    val peer = state.getJSONObject("peers").getJSONObject(peerID)
    assertEquals(false, peer.has("deviceId"))
    assertEquals(false, peer.has("devicePublicKey"))
    assertEquals(false, peer.has("deviceCertificate"))
    assertEquals(false, peer.has("deviceSignedPreKey"))
    assertEquals("ee".repeat(32), peer.optString("publicKey"))
    assertEquals(false, state.getJSONObject("sessions").has(peerID))
  }

  @Test
  fun `keeps the other peers untouched`() {
    val state = state()
    assertTrue(DevicePurge.apply(state, revokedDevice))
    assertEquals("dd".repeat(32), state.getJSONObject("peers").getJSONObject(otherPeer).optString("deviceId"))
    assertTrue(state.getJSONObject("sessions").has(otherPeer))
  }

  @Test
  fun `matches the device id case insensitively`() {
    val state = state()
    assertTrue(DevicePurge.apply(state, revokedDevice.uppercase()))
    assertEquals(false, state.getJSONObject("peers").getJSONObject(peerID).has("deviceId"))
  }

  @Test
  fun `ignores unknown devices and malformed ids`() {
    val state = state()
    assertFalse(DevicePurge.apply(state, "99".repeat(32)))
    assertFalse(DevicePurge.apply(state, "no-es-un-id"))
    assertTrue(state.getJSONObject("sessions").has(peerID))
  }

  @Test
  fun `survives a state without peers`() {
    assertFalse(DevicePurge.apply(JSONObject(), revokedDevice))
  }
}
