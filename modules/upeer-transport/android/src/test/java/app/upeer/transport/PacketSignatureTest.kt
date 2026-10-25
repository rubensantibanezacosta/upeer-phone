package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketSignatureTest {
  private val deviceId = "ab".repeat(32)

  @Test
  fun `a packet without senderDeviceId is signed by the account`() {
    assertEquals(PacketSignature.Account, PacketSignature.of(JSONObject().put("type", "ACK")))
  }

  @Test
  fun `an empty senderDeviceId is signed by the account`() {
    assertEquals(PacketSignature.Account, PacketSignature.of(JSONObject().put("type", "ACK").put("senderDeviceId", "")))
  }

  @Test
  fun `a device packet is linked`() {
    val signature = PacketSignature.of(JSONObject().put("type", "ACK").put("senderDeviceId", deviceId))

    assertTrue(signature is PacketSignature.Linked)
    assertEquals(deviceId, (signature as PacketSignature.Linked).deviceId)
  }

  @Test
  fun `the device id is lowercased`() {
    val signature = PacketSignature.of(JSONObject().put("type", "ACK").put("senderDeviceId", deviceId.uppercase()))

    assertEquals(deviceId, (signature as PacketSignature.Linked).deviceId)
  }

  @Test
  fun `a short senderDeviceId is rejected`() {
    assertThrows(IllegalArgumentException::class.java) {
      PacketSignature.of(JSONObject().put("type", "ACK").put("senderDeviceId", "ab"))
    }
  }

  @Test
  fun `a non hex senderDeviceId is rejected`() {
    assertThrows(IllegalArgumentException::class.java) {
      PacketSignature.of(JSONObject().put("type", "ACK").put("senderDeviceId", "zz".repeat(32)))
    }
  }
}
