package app.upeer.transport

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReceivePolicyTest {
  private fun json(vararg pairs: Pair<String, Any>): JSONObject {
    val payload = JSONObject()
    payload.put("fileId", "abc")
    payload.put("fileName", "informe.pdf")
    payload.put("fileSize", 1024)
    payload.put("chunkSize", 512)
    payload.put("totalChunks", 2)
    for ((key, value) in pairs) payload.put(key, value)
    return payload
  }

  @Test
  fun `acepta una propuesta coherente`() {
    val proposal = FileReceivePolicy.proposal(json())

    assertEquals("abc", proposal.fileId)
    assertEquals("informe.pdf", proposal.fileName)
    assertEquals(1024L, proposal.fileSize)
    assertEquals(2, proposal.totalChunks)
    assertEquals("application/octet-stream", proposal.mimeType)
    assertNull(proposal.caption)
  }

  @Test
  fun `normaliza el nombre y descarta la ruta`() {
    assertEquals("foto.jpg", FileReceivePolicy.safeFileName("../../privado/foto.jpg"))
    assertEquals("foto.jpg", FileReceivePolicy.safeFileName("C:\\Users\\me\\foto.jpg"))
  }

  @Test
  fun `acepta un archivo vacío sin trozos`() {
    val proposal = FileReceivePolicy.proposal(json("fileSize" to 0, "totalChunks" to 0))

    assertEquals(0L, proposal.fileSize)
    assertEquals(0, proposal.totalChunks)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un archivo vacío con trozos`() {
    FileReceivePolicy.proposal(json("fileSize" to 0))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza trozos incoherentes`() {
    FileReceivePolicy.proposal(json("totalChunks" to 5))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un trozo mayor que el máximo`() {
    FileReceivePolicy.proposal(json("chunkSize" to FileReceivePolicy.MAX_CHUNK_SIZE + 1))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un archivo por encima del límite`() {
    FileReceivePolicy.proposal(json("fileSize" to FileReceivePolicy.MAX_FILE_SIZE + 1))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un fileId vacío`() {
    FileReceivePolicy.proposal(json("fileId" to ""))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un nombre vacío`() {
    FileReceivePolicy.proposal(json("fileName" to "   "))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza conversaciones que no son de grupo`() {
    FileReceivePolicy.proposal(json("chatUpeerId" to "contacto"))
  }

  @Test
  fun `acepta una conversación de grupo`() {
    val proposal = FileReceivePolicy.proposal(json("chatUpeerId" to "grp-1234"))

    assertEquals("grp-1234", proposal.chatUpeerId)
  }

  @Test
  fun `recorta la nota de voz y el texto de acompañamiento`() {
    val proposal = FileReceivePolicy.proposal(
      json("isVoiceNote" to true, "caption" to "a".repeat(FileReceivePolicy.MAX_CAPTION_LENGTH + 20))
    )

    assertTrue(proposal.isVoiceNote)
    assertEquals(FileReceivePolicy.MAX_CAPTION_LENGTH, proposal.caption?.length)
  }
}

class FileChunkAssemblerTest {
  private fun digest(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { String.format("%02x", it) }

  private fun proposal(fileSize: Long, chunkSize: Int, hash: String? = null): FileReceivePolicy.Proposal =
    FileReceivePolicy.Proposal(
      fileId = "abc",
      fileName = "datos.bin",
      fileSize = fileSize,
      totalChunks = if (fileSize == 0L) 0 else Math.ceil(fileSize.toDouble() / chunkSize.toDouble()).toInt(),
      chunkSize = chunkSize,
      mimeType = "application/octet-stream",
      fileHash = hash,
      caption = null,
      isVoiceNote = false,
      chatUpeerId = null,
      messageId = null
    )

  private fun tempFile(): File = File.createTempFile("upeer-file", ".bin").also {
    it.delete()
    it.deleteOnExit()
  }

  @Test
  fun `ensambla los trozos en orden`() {
    val content = ByteArray(10) { it.toByte() }
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(10, 4), target)

    assertEquals(FileChunkAssembler.Outcome.STORED, assembler.accept(0, content.copyOfRange(0, 4), null))
    assertEquals(FileChunkAssembler.Outcome.STORED, assembler.accept(1, content.copyOfRange(4, 8), null))
    assertEquals(FileChunkAssembler.Outcome.STORED, assembler.accept(2, content.copyOfRange(8, 10), null))
    assertTrue(assembler.isComplete())
    assertEquals(100, assembler.progress())

    val file = assembler.finish()
    assertTrue(content.contentEquals(file.readBytes()))
    file.delete()
  }

  @Test
  fun `ensambla aunque los trozos lleguen desordenados`() {
    val content = ByteArray(10) { (it * 3).toByte() }
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(10, 4), target)

    assembler.accept(2, content.copyOfRange(8, 10), null)
    assembler.accept(0, content.copyOfRange(0, 4), null)
    assembler.accept(1, content.copyOfRange(4, 8), null)

    val file = assembler.finish()
    assertTrue(content.contentEquals(file.readBytes()))
    file.delete()
  }

  @Test
  fun `marca los duplicados sin duplicar bytes`() {
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(8, 4), target)
    val chunk = ByteArray(4) { 1 }

    assertEquals(FileChunkAssembler.Outcome.STORED, assembler.accept(0, chunk, null))
    assertEquals(FileChunkAssembler.Outcome.DUPLICATE, assembler.accept(0, chunk, null))
    assertEquals(4L, assembler.receivedBytes())
    assertEquals(50, assembler.progress())
    assembler.close()
    target.delete()
  }

  @Test
  fun `rechaza índices fuera de rango`() {
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(8, 4), target)

    assertEquals(FileChunkAssembler.Outcome.OUT_OF_RANGE, assembler.accept(2, ByteArray(4), null))
    assertEquals(FileChunkAssembler.Outcome.OUT_OF_RANGE, assembler.accept(-1, ByteArray(4), null))
    assembler.close()
    target.delete()
  }

  @Test
  fun `rechaza un trozo con longitud incorrecta`() {
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(10, 4), target)

    assertEquals(FileChunkAssembler.Outcome.OUT_OF_RANGE, assembler.accept(2, ByteArray(4), null))
    assertEquals(FileChunkAssembler.Outcome.OUT_OF_RANGE, assembler.accept(0, ByteArray(3), null))
    assembler.close()
    target.delete()
  }

  @Test
  fun `rechaza un trozo cuyo hash no coincide`() {
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(8, 4), target)
    val chunk = ByteArray(4) { 7 }

    assertEquals(FileChunkAssembler.Outcome.HASH_MISMATCH, assembler.accept(0, chunk, digest(ByteArray(4) { 8 })))
    assertEquals(0L, assembler.receivedBytes())
    assertEquals(FileChunkAssembler.Outcome.STORED, assembler.accept(0, chunk, digest(chunk)))
    assembler.close()
    target.delete()
  }

  @Test(expected = IllegalArgumentException::class)
  fun `no deja cerrar una recepción incompleta`() {
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(8, 4), target)

    try {
      assembler.accept(0, ByteArray(4), null)
      assembler.finish()
    } finally {
      assembler.close()
      target.delete()
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un archivo cuyo hash global no coincide`() {
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(4, 4, "0".repeat(64)), target)

    try {
      assembler.accept(0, ByteArray(4) { 2 }, null)
      assembler.finish()
    } finally {
      assembler.close()
      target.delete()
    }
  }

  @Test
  fun `acepta un archivo vacío`() {
    val target = tempFile()
    val assembler = FileChunkAssembler(proposal(0, 4), target)

    assertTrue(assembler.isComplete())
    assertEquals(0, assembler.progress())
    assertEquals(0L, assembler.finish().length())
    target.delete()
  }
}
