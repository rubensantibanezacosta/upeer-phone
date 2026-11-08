package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.json.JSONObject

/**
 * El PING anuncia el dispositivo del remitente.
 *
 * El contacto necesita `devicePublicKey`, `deviceId`, `deviceCertificate` y
 * `deviceSignedPreKey` para completar el X3DH por dispositivo. Sin ellos su
 * `deviceSignedPreKey` queda vacío y el mensaje cifrado contra el dispositivo no
 * descifra, aunque el "escribiendo" (que no pasa por el ratchet) sí llegue.
 */
class ControlPingsDeviceTest {
  @Test
  fun `el PING conserva el tipo y la actividad`() {
    val packet = ControlPings.active(1794138374073L, shareActivity = true)
    assertEquals("PING", packet.optString("type"))
    assertEquals(1794138374073L, packet.optLong("activeAt"))
  }

  @Test
  fun `el PING omite la actividad cuando no se comparte`() {
    val packet = ControlPings.active(1794138374073L, shareActivity = false)
    assertEquals(false, packet.has("activeAt"))
  }

  @Test
  fun `los campos de dispositivo se pueden volcar en el paquete`() {
    // Mismo volcado que hace ControlSender con `buildMyDeviceHandshakeFields` en escritorio.
    val fields = JSONObject()
      .put("devicePublicKey", "cc".repeat(32))
      .put("deviceId", "dd".repeat(32))
      .put("deviceCertificate", "cert")
      .put("deviceSignedPreKey", JSONObject().put("spkPub", "ee".repeat(32)))
    val packet = ControlPings.active(1794138374073L, shareActivity = false)
    for (key in fields.keys()) packet.put(key, fields.get(key))

    assertNotNull(packet.optString("devicePublicKey").ifEmpty { null })
    assertEquals("dd".repeat(32), packet.optString("deviceId"))
    assertEquals("cert", packet.optString("deviceCertificate"))
    assertEquals("ee".repeat(32), packet.optJSONObject("deviceSignedPreKey")?.optString("spkPub"))
  }
}
