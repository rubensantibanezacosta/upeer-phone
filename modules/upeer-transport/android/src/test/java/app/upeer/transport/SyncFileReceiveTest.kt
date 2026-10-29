package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncFileReceiveTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val deviceId = "a".repeat(64)
  private val sender = SyncSender(deviceId, JSONObject().put("deviceId", deviceId))
  private val recipient = "b".repeat(64)

  private fun payload(requestId: String, bytes: ByteArray, index: Int): SyncFilePackets.Chunk {
    val packet = SyncFilePackets.chunk(sender, recipient, requestId, index, bytes)!!
    return SyncFilePackets.parseChunk(packet)!!
  }

  private fun meta(total: Int): SyncFilePackets.ResMeta =
    SyncFilePackets.ResMeta("foto.png", SyncFilePackets.CHUNK_SIZE.toLong() * total, SyncFilePackets.CHUNK_SIZE, total)

  @Test
  fun `reensambla el fichero a partir de los tramos`() {
    val root = folder.newFolder("attachments")
    SyncFileReceive.attachDirectory(root)
    val first = ByteArray(SyncFilePackets.CHUNK_SIZE) { (it % 251).toByte() }
    val second = ByteArray(37) { (it + 7).toByte() }
    val expected = first + second

    assertEquals(
      File(File(root, "synced"), "file-1.png").absolutePath,
      SyncFileReceive.open("req12345678", "file-1", meta(2)),
    )
    assertEquals(SyncFileReceive.Outcome.PROGRESS, SyncFileReceive.write(payload("req12345678", first, 0), "file-1"))
    assertEquals(SyncFileReceive.Outcome.DONE, SyncFileReceive.write(payload("req12345678", second, 1), "file-1"))
    val savedPath = SyncFileReceive.finish("req12345678")

    assertEquals(File(File(root, "synced"), "file-1.png").absolutePath, savedPath)
    assertArrayEquals(expected, File(savedPath!!).readBytes())
  }

  @Test
  fun `descarta la recepcion cuando un tramo llega fuera de orden`() {
    val root = folder.newFolder("attachments")
    SyncFileReceive.attachDirectory(root)
    SyncFileReceive.open("req87654321", "file-2", meta(2))
    val outcome = SyncFileReceive.write(payload("req87654321", ByteArray(16) { 1 }, 1), "file-2")

    assertEquals(SyncFileReceive.Outcome.DISCARDED, outcome)
    assertNull(SyncFileReceive.finish("req87654321"))
  }

  @Test
  fun `no deja recibir sin carpeta de adjuntos`() {
    SyncFileReceive.attachDirectory(folder.newFolder("vacia"))
    assertTrue(SyncFileReceive.open("req00000001", "file-3", meta(1))!!.endsWith("file-3.png"))
    SyncFileReceive.close("req00000001")
    assertNull(SyncFileReceive.finish("req00000001"))
  }
}
