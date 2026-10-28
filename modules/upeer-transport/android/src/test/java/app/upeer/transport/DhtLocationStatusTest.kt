package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtLocationStatusTest {
  private var now = 1_000L
  private val status = DhtLocationStatus { now }

  @Test
  fun `empty status has no publication and no error`() {
    val json = status.toJson()

    assertEquals(0L, json.optLong("publishedAt"))
    assertEquals(0, json.optInt("nodes"))
    assertTrue(json.isNull("error"))
  }

  @Test
  fun `publication records time, sequence and nodes`() {
    status.notePublished(7L, 12)

    val json = status.toJson()

    assertEquals(1_000L, json.optLong("publishedAt"))
    assertEquals(7L, json.optLong("dhtSeq"))
    assertEquals(12, json.optInt("nodes"))
  }

  @Test
  fun `negative node counts are clamped`() {
    status.notePublished(1L, -4)

    assertEquals(0, status.toJson().optInt("nodes"))
  }

  @Test
  fun `fanout is recorded separately`() {
    now = 5_000L
    status.noteFanout(3)

    val json = status.toJson()

    assertEquals(5_000L, json.optLong("fanoutAt"))
    assertEquals(3, json.optInt("fanout"))
  }

  @Test
  fun `a failure keeps its kind and time`() {
    now = 2_500L
    status.noteFailure(DhtLocationStatus.KIND_NETWORK, "socket cerrado")

    val error = status.toJson().optJSONObject("error")

    assertEquals("red", error?.optString("kind"))
    assertEquals("socket cerrado", error?.optString("message"))
    assertEquals(2_500L, error?.optLong("at"))
    assertEquals("socket cerrado", status.failureOf()?.message)
  }

  @Test
  fun `publishing clears the previous failure`() {
    status.noteFailure(DhtLocationStatus.KIND_CERTIFICATE, "certificado caducado")
    status.notePublished(2L, 1)

    assertNull(status.failureOf())
    assertTrue(status.toJson().isNull("error"))
  }

  @Test
  fun `failure messages hide addresses and fingerprints`() {
    val sanitized = DhtLocationStatus.sanitize("No se pudo conectar a 2001:db8::1 ni a 192.168.1.4 con la clave ${"ab".repeat(32)}")

    assertFalse(sanitized.contains("2001:db8::1"))
    assertFalse(sanitized.contains("192.168.1.4"))
    assertFalse(sanitized.contains("ab".repeat(32)))
    assertTrue(sanitized.contains("[dirección]"))
    assertTrue(sanitized.contains("[clave]"))
  }

  @Test
  fun `failure messages keep useful detail and are capped`() {
    assertEquals("Error sin detalle", DhtLocationStatus.sanitize("   "))

    val long = DhtLocationStatus.sanitize("x".repeat(400))

    assertEquals(161, long.length)
    assertTrue(long.endsWith("…"))
  }

  @Test
  fun `serialised error is null instead of a missing key`() {
    val json = JSONObject(status.toJson().toString())

    assertTrue(json.has("error"))
    assertTrue(json.isNull("error"))
  }
}
