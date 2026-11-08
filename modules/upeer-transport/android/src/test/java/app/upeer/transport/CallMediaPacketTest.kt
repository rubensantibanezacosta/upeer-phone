package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * El sobre binario de `CALL_MEDIA` tiene que ser byte a byte el del escritorio.
 *
 * El `base64` de abajo **no está escrito a mano**: sale de ejecutar `encodeBinaryCallMedia` del
 * escritorio (`upper-desktop/src/main_process/network/binaryCallMedia.ts`). La media no viaja como JSON
 * con base64 dentro porque eso hincharía cada paquete un 33 %, y de una llamada salen cientos.
 *
 * Lo que más se escapa es el orden de bytes: el escritorio escribe la longitud de los metadatos con
 * `writeUInt32BE`, y Android es little endian por naturaleza. Con el orden mal, el `JSONObject` de los
 * metadatos se parsea desde un offset equivocado y el paquete se descarta en silencio.
 */
class CallMediaPacketTest {

  /**
   * Frame del escritorio para `callId: 'abc123'` con datos `[1..8]`, `senderUpeerId: 'me'`,
   * `timestamp: 1790000000000` y una firma de relleno.
   */
  private val desktopFrame = Base64.getDecoder().decode(
    "VUNNMQAAAPR7InR5cGUiOiJDQUxMX01FRElBIiwiY2FsbElkIjoiYWJjMTIzIiwibWVkaWFIYXNoIjoiNjY4NDBkZGExNTRlOGExMTNjMzFkZDBhZDMyZjdmM2EzNjZhODBlODEzNjk3OWQ4ZjVhMTAxZDNkMjlkNmY3MiIsInNlbmRlclVwZWVySWQiOiJtZSIsInRpbWVzdGFtcCI6MTc5MDAwMDAwMDAwMCwic2lnbmF0dXJlIjoiYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYSJ9AQIDBAUGBwg=",
  )

  @Test
  fun `el hash coincide con el del escritorio`() {
    // SHA-256 de los bytes 1..8, tal y como lo calculó el escritorio.
    assertEquals("66840dda154e8a113c31dd0ad32f7f3a366a80e8136979d8f5a101d3d29d6f72", CallMediaPacket.hash(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
  }

  @Test
  fun `reconoce el sobre del escritorio`() {
    assertTrue(CallMediaPacket.isBinary(desktopFrame))
    assertFalse(CallMediaPacket.isBinary("{\"type\":\"CALL_MEDIA\"}".toByteArray()))
  }

  @Test
  fun `decodifica el sobre del escritorio y saca los metadatos y el frame`() {
    val packet = CallMediaPacket.decode(desktopFrame)
    assertNotNull("el sobre del escritorio tiene que decodificarse", packet)
    assertEquals("CALL_MEDIA", packet!!.optString("type"))
    assertEquals("abc123", packet.optString("callId"))
    assertEquals(1790000000000L, packet.optLong("timestamp"))
    val data = packet.opt("data") as ByteArray
    assertTrue(data.contentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
  }

  @Test
  fun `lo que codifica el movil lo vuelve a leer`() {
    val metadata = JSONObject()
      .put("type", "CALL_MEDIA")
      .put("callId", "c1")
      .put("timestamp", 1_790_000_000_000L)
    val encoded = CallMediaPacket.encode(metadata, byteArrayOf(9, 8, 7))
    val decoded = CallMediaPacket.decode(encoded)!!
    assertEquals("c1", decoded.optString("callId"))
    assertTrue((decoded.opt("data") as ByteArray).contentEquals(byteArrayOf(9, 8, 7)))
  }

  @Test
  fun `un frame manipulado se descarta`() {
    // El hash es lo que evita reproducir ruido: si alguien toca el frame por el camino, no pasa.
    val encoded = CallMediaPacket.encode(
      JSONObject().put("type", "CALL_MEDIA").put("callId", "c1"),
      byteArrayOf(9, 8, 7),
    )
    val tampered = encoded.copyOf()
    tampered[tampered.size - 1] = 0x42
    assertNull(CallMediaPacket.decode(tampered))
  }

  @Test
  fun `un sobre invalido se descarta sin lanzar`() {
    // Lo alimenta la red: nada de esto puede tumbar la llamada.
    assertNull(CallMediaPacket.decode(ByteArray(0)))
    assertNull(CallMediaPacket.decode(ByteArray(7)))
    // Mágica correcta pero longitud imposible.
    val badLength = CallMediaPacket.encode(JSONObject().put("type", "CALL_MEDIA"), byteArrayOf(1))
    badLength[7] = 0x7F
    assertNull(CallMediaPacket.decode(badLength))
    // Mágica correcta y longitud a cero.
    val zeroLength = badLength.copyOf()
    for (index in 4..7) zeroLength[index] = 0
    assertNull(CallMediaPacket.decode(zeroLength))
    // JSON corrupto en los metadatos.
    val badJson = ByteArray(CallMediaPacket.HEADER_BYTES + 3 + 1)
    val magic = CallMediaPacket.MAGIC
    System.arraycopy(magic, 0, badJson, 0, magic.size)
    badJson[7] = 3
    badJson[8] = '{'.code.toByte(); badJson[9] = 'x'.code.toByte(); badJson[10] = '}'.code.toByte()
    badJson[11] = 1
    assertNull(CallMediaPacket.decode(badJson))
  }

  @Test
  fun `un paquete que no es CALL_MEDIA se descarta`() {
    val encoded = CallMediaPacket.encode(JSONObject().put("type", "CALL_END").put("callId", "c1"), byteArrayOf(1))
    assertNull(CallMediaPacket.decode(encoded))
  }

  @Test
  fun `los metadatos con el frame dentro se reescriben como se envian`() {
    // `encode` no puede arrastrar el campo `data` dentro de los metadatos: se duplicaría el frame.
    val withData = JSONObject().put("type", "CALL_MEDIA").put("callId", "c1").put("data", "resto")
    val encoded = CallMediaPacket.encode(withData, byteArrayOf(1, 2))
    val decoded = CallMediaPacket.decode(encoded)!!
    assertTrue((decoded.opt("data") as ByteArray).contentEquals(byteArrayOf(1, 2)))
  }

  @Test
  fun `el tope por paquete coincide con el del escritorio`() {
    // El escritorio descarta CALL_MEDIA por encima de 600 000 B por la fragmentación de UDP.
    assertEquals(600_000, CallMediaPacket.MAX_PACKET_BYTES)
  }
}
