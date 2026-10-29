package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFilePacketsTest {
  private val deviceId = "a".repeat(64)
  private val sender = SyncSender(deviceId, JSONObject().put("deviceId", deviceId))

  @Test
  fun `reparte el fichero en tramos del tamano fijado`() {
    assertEquals(0, SyncFilePackets.chunkCount(0, SyncFilePackets.CHUNK_SIZE))
    assertEquals(1, SyncFilePackets.chunkCount(1, SyncFilePackets.CHUNK_SIZE))
    assertEquals(1, SyncFilePackets.chunkCount(SyncFilePackets.CHUNK_SIZE.toLong(), SyncFilePackets.CHUNK_SIZE))
    assertEquals(2, SyncFilePackets.chunkCount(SyncFilePackets.CHUNK_SIZE + 1L, SyncFilePackets.CHUNK_SIZE))
  }

  @Test
  fun `calcula los limites de cada tramo`() {
    assertEquals(0L to 10, SyncFilePackets.bounds(0, 10, 25))
    assertEquals(20L to 5, SyncFilePackets.bounds(2, 10, 25))
    assertNull(SyncFilePackets.bounds(3, 10, 25))
    assertNull(SyncFilePackets.bounds(-1, 10, 25))
  }

  @Test
  fun `ida y vuelta de la peticion`() {
    val packet = SyncFilePackets.request(sender, "b".repeat(64), "abc12345", "8f14e45f-ceea-467a-9b1f-000000000001")
    val parsed = SyncFilePackets.parseRequest(packet!!)
    assertEquals("abc12345", parsed!!.requestId)
    assertEquals("8f14e45f-ceea-467a-9b1f-000000000001", parsed.fileId)
  }

  @Test
  fun `rechaza rutas y peticiones sin formato`() {
    assertNull(SyncFilePackets.request(sender, "b".repeat(64), "abc12345", "../secreto"))
    assertNull(SyncFilePackets.request(sender, "b".repeat(64), "abc12345", ""))
    assertNull(SyncFilePackets.request(sender, "b".repeat(64), "corto", "f1"))
    assertNull(SyncFilePackets.parseRequest(JSONObject().put("requestId", "abc12345")))
    assertNull(SyncFilePackets.parseRequest(JSONObject().put("fileId", "f1")))
  }

  @Test
  fun `una respuesta correcta lleva los metadatos`() {
    val meta = SyncFilePackets.ResMeta("foto.png", 1000L, SyncFilePackets.CHUNK_SIZE, 2)
    val packet = SyncFilePackets.response(sender, "b".repeat(64), "abc12345", "f1", meta)!!
    val parsed = SyncFilePackets.parseResponse(packet)!!
    assertTrue(packet.optBoolean("ok"))
    assertEquals("f1", parsed.fileId)
    assertEquals("foto.png", parsed.meta!!.fileName)
    assertEquals(2, parsed.meta!!.totalChunks)
  }

  @Test
  fun `una negativa viaja sin metadatos`() {
    val packet = SyncFilePackets.response(sender, "b".repeat(64), "abc12345", "f1", null, "not-here")!!
    val parsed = SyncFilePackets.parseResponse(packet)!!
    assertFalse(packet.optBoolean("ok"))
    assertEquals("not-here", parsed.reason)
    assertNull(parsed.meta)
  }

  @Test
  fun `descarta respuestas fuera de rango`() {
    val base = JSONObject()
      .put("requestId", "abc12345")
      .put("fileId", "f1")
      .put("ok", true)
      .put("fileName", "foto.png")
      .put("fileSize", 1000L)
      .put("chunkSize", SyncFilePackets.CHUNK_SIZE)
      .put("totalChunks", 2)
    assertNull(SyncFilePackets.parseResponse(JSONObject(base.toString()).put("totalChunks", 0)))
    assertNull(SyncFilePackets.parseResponse(JSONObject(base.toString()).put("fileSize", -1L)))
    assertNull(SyncFilePackets.parseResponse(JSONObject(base.toString()).put("fileName", "")))
    assertNull(SyncFilePackets.parseResponse(JSONObject(base.toString()).put("chunkSize", SyncFilePackets.CHUNK_SIZE * 2)))
  }

  @Test
  fun `ida y vuelta de un tramo`() {
    val data = ByteArray(64) { index -> (index % 251).toByte() }
    val packet = SyncFilePackets.chunk(sender, "b".repeat(64), "abc12345", 3, data)!!
    val parsed = SyncFilePackets.parseChunk(packet)!!
    assertEquals(3, parsed.index)
    assertArrayEquals(data, parsed.bytes)
  }

  @Test
  fun `ida y vuelta con cualquier resto de longitud`() {
    for (size in 1..7) {
      val data = ByteArray(size) { index -> ((index * 37) % 256).toByte() }
      val packet = SyncFilePackets.chunk(sender, "b".repeat(64), "abc12345", 0, data)!!
      assertArrayEquals(data, SyncFilePackets.parseChunk(packet)!!.bytes)
    }
  }

  @Test
  fun `el tramo viaja en base64 para no duplicar los bytes`() {
    val data = ByteArray(SyncFilePackets.CHUNK_SIZE)
    val packet = SyncFilePackets.chunk(sender, "b".repeat(64), "abc12345", 0, data)!!
    val encoded = packet.optString("data")
    assertEquals((SyncFilePackets.CHUNK_SIZE + 2) / 3 * 4, encoded.length)
  }

  @Test
  fun `el base64 sigue el alfabeto estandar para entenderse con el escritorio`() {
    val vectors = mapOf(
      "" to "",
      "f" to "Zg==",
      "fo" to "Zm8=",
      "foo" to "Zm9v",
      "foob" to "Zm9vYg==",
      "fooba" to "Zm9vYmE=",
      "foobar" to "Zm9vYmFy",
    )
    for ((plain, expected) in vectors) {
      val bytes = plain.toByteArray(Charsets.UTF_8)
      val packet = SyncFilePackets.chunk(sender, "b".repeat(64), "abc12345", 0, bytes) ?: continue
      assertEquals(expected, packet.optString("data"))
    }
  }

  @Test
  fun `descarta tramos vacios o fuera de rango`() {
    assertNull(SyncFilePackets.chunk(sender, "b".repeat(64), "abc12345", 0, ByteArray(0)))
    assertNull(SyncFilePackets.chunk(sender, "b".repeat(64), "abc12345", -1, ByteArray(4)))
    assertNull(SyncFilePackets.chunk(sender, "b".repeat(64), "abc12345", 0, ByteArray(SyncFilePackets.CHUNK_SIZE + 1)))
    assertNull(SyncFilePackets.parseChunk(JSONObject().put("requestId", "abc12345").put("index", 0).put("data", "")))
  }
}
