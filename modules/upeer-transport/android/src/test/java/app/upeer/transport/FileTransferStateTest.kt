package app.upeer.transport

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTransferStateTest {
  @get:Rule
  val temporary = TemporaryFolder()

  private val aesKey = ByteArray(32) { it.toByte() }

  private fun proposal(fileId: String = "file-1"): JSONObject = JSONObject()
    .put("fileId", fileId)
    .put("fileName", "informe.pdf")
    .put("fileSize", 12)
    .put("chunkSize", 8)
    .put("totalChunks", 2)
    .put("mimeType", "application/pdf")

  private fun state(): FileTransferState = FileTransferState { temporary.root }

  @Test
  fun `guarda la clave cuando tiene el tamano correcto`() {
    val transfers = state()

    assertTrue(transfers.acceptKey("file-1", aesKey))
    assertEquals(aesKey, transfers.keyFor("file-1"))
  }

  @Test
  fun `rechaza una clave que no es de 32 bytes`() {
    val transfers = state()

    assertTrue(!transfers.acceptKey("file-1", ByteArray(16)))
    assertNull(transfers.keyFor("file-1"))
  }

  @Test
  fun `abre una transferencia en el directorio de la conversacion`() {
    val transfers = state()
    val entry = transfers.open(FileReceivePolicy.proposal(proposal()), "peer-1")

    assertNotNull(entry)
    assertEquals("peer-1", entry?.senderUpeerId)
    assertTrue(entry!!.file.absolutePath.contains("/attachments/direct/"))
    transfers.cancel("file-1")
  }

  @Test
  fun `agrupa los adjuntos de grupo bajo el identificador del grupo`() {
    val transfers = state()
    val entry = transfers.open(
      FileReceivePolicy.proposal(proposal().put("chatUpeerId", "grp-abc")),
      "peer-1",
    )

    assertTrue(entry!!.file.absolutePath.contains("/attachments/grp-abc/"))
    transfers.cancel("file-1")
  }

  @Test
  fun `ensambla los trozos en orden hasta completar`() {
    val transfers = state()
    val entry = transfers.open(FileReceivePolicy.proposal(proposal()), "peer-1")!!

    assertEquals(FileChunkAssembler.Outcome.STORED, entry.assembler.accept(0, "01234567".toByteArray(), null))
    assertEquals(FileChunkAssembler.Outcome.STORED, entry.assembler.accept(1, "8901".toByteArray(), null))
    assertTrue(entry.assembler.isComplete())
    assertEquals(12, entry.assembler.finish().length())
    transfers.cancel("file-1")
  }

  @Test
  fun `cancelar borra el archivo parcial`() {
    val transfers = state()
    val entry = transfers.open(FileReceivePolicy.proposal(proposal()), "peer-1")!!
    entry.assembler.accept(0, "01234567".toByteArray(), null)
    val path = entry.file

    transfers.cancel("file-1")

    assertTrue(!path.exists())
    assertNull(transfers.find("file-1"))
  }

  @Test
  fun `el estado expone el progreso de las transferencias activas`() {
    val transfers = state()
    val entry = transfers.open(FileReceivePolicy.proposal(proposal()), "peer-1")!!
    entry.assembler.accept(0, "01234567".toByteArray(), null)

    val reported = JSONArray(transfers.attachmentsJSON()).getJSONObject(0)

    assertEquals("file-1", reported.optString("fileId"))
    assertEquals(66, reported.optInt("progress"))
  }

  @Test
  fun `aceptar dos veces la misma clave la conserva`() {
    val transfers = state()

    transfers.acceptKey("file-1", aesKey)
    transfers.acceptKey("file-1", aesKey)

    assertEquals(aesKey, transfers.keyFor("file-1"))
  }

  @Test
  fun `el archivo final vive dentro del almacenamiento recibido`() {
    val transfers = state()
    val entry = transfers.open(FileReceivePolicy.proposal(proposal()), "peer-1")!!

    assertTrue(entry.file.absolutePath.startsWith(File(temporary.root, "attachments").absolutePath))
    transfers.cancel("file-1")
  }

  @Test
  fun `completar la transferencia olvida la clave`() {
    val transfers = state()
    val entry = transfers.open(FileReceivePolicy.proposal(proposal()), "peer-1")!!
    transfers.acceptKey("file-1", aesKey)
    entry.assembler.accept(0, "01234567".toByteArray(), null)
    entry.assembler.accept(1, "8901".toByteArray(), null)
    entry.assembler.finish()

    transfers.complete("file-1")

    assertNull(transfers.find("file-1"))
    assertNull(transfers.keyFor("file-1"))
  }
}
