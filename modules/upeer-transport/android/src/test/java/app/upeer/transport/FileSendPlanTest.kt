package app.upeer.transport

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSendPlanTest {
  @get:Rule
  val temporary = TemporaryFolder()

  private fun file(name: String, size: Int): File {
    val target = temporary.newFile(name)
    target.writeBytes(ByteArray(size) { (it % 251).toByte() })
    return target
  }

  private fun expectedHash(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .joinToString("") { "%02x".format(it) }

  @Test
  fun `el hash del archivo coincide con un sha256 completo`() {
    val target = file("informe.pdf", 300_000)
    val plan = FileSendPlan.plan(target.absolutePath, "informe.pdf", "application/pdf")
    assertEquals(expectedHash(target.readBytes()), plan.fileHash)
  }

  @Test
  fun `el hash no depende del tamano de trozo`() {
    val target = file("grande.bin", 200_000)
    val big = FileSendPlan.plan(target.absolutePath, "grande.bin", "", chunkSize = 64 * 1024)
    val small = FileSendPlan.plan(target.absolutePath, "grande.bin", "", chunkSize = 8_000)
    assertEquals(big.fileHash, small.fileHash)
    assertTrue(small.totalChunks > big.totalChunks)
  }

  @Test
  fun `reconstruye el archivo uniendo los trozos`() {
    val target = file("video.mp4", 150_123)
    val plan = FileSendPlan.plan(target.absolutePath, "video.mp4", "video/mp4", chunkSize = 16_384)
    val joined = java.io.ByteArrayOutputStream()
    for (index in 0 until plan.totalChunks) {
      joined.write(FileSendPlan.readChunk(plan.file, index, plan.chunkSize, plan.fileSize)!!)
    }
    assertTrue(joined.toByteArray().contentEquals(target.readBytes()))
    assertNull(FileSendPlan.readChunk(plan.file, plan.totalChunks, plan.chunkSize, plan.fileSize))
  }

  @Test
  fun `el ultimo trozo es mas corto que el resto`() {
    val target = file("corto.bin", 10)
    val plan = FileSendPlan.plan(target.absolutePath, "corto.bin", "", chunkSize = 4)
    assertEquals(3, plan.totalChunks)
    assertEquals(4, FileSendPlan.readChunk(plan.file, 0, plan.chunkSize, plan.fileSize)!!.size)
    assertEquals(2, FileSendPlan.readChunk(plan.file, 2, plan.chunkSize, plan.fileSize)!!.size)
  }

  @Test
  fun `un archivo vacio no produce trozos`() {
    val target = file("vacio.txt", 0)
    val plan = FileSendPlan.plan(target.absolutePath, "vacio.txt", "text/plain")
    assertEquals(0, plan.totalChunks)
    assertEquals(0, FileSendPlan.totalChunks(0, 4))
    assertNull(FileSendPlan.readChunk(plan.file, 0, plan.chunkSize, plan.fileSize))
  }

  @Test
  fun `sanea el nombre y rellena el mime por defecto`() {
    val target = file("x.bin", 3)
    val plan = FileSendPlan.plan(target.absolutePath, "../../etc/passwd", "")
    assertEquals("passwd", plan.fileName)
    assertEquals("application/octet-stream", plan.mimeType)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un archivo inexistente`() {
    FileSendPlan.plan(File(temporary.root, "no-existe.bin").absolutePath, "no-existe.bin", "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rechaza un tamano de trozo fuera de rango`() {
    FileSendPlan.totalChunks(100, FileSendPlan.MAX_CHUNK_SIZE + 1)
  }

  @Test
  fun `indexa los trozos por offset`() {
    val target = file("offset.bin", 9)
    val plan = FileSendPlan.plan(target.absolutePath, "offset.bin", "", chunkSize = 3)
    val third = FileSendPlan.readChunk(plan.file, 2, plan.chunkSize, plan.fileSize)!!
    val bytes = target.readBytes()
    assertTrue(third.contentEquals(bytes.copyOfRange(6, 9)))
    assertNull(FileSendPlan.readChunk(plan.file, -1, plan.chunkSize, plan.fileSize))
  }

  @Test
  fun `limpia los caracteres que el receptor rechaza`() {
    assertEquals("informe_.pdf", FileSendPlan.networkFileName("informe?.pdf"))
    assertEquals("a_b_c_d_e_f_g.txt", FileSendPlan.networkFileName("a<b>c:d\"e|f*g.txt"))
    assertEquals("foto_final.jpg", FileSendPlan.networkFileName("foto..final.jpg"))
  }

  @Test
  fun `el nombre nunca queda vacio`() {
    assertEquals("archivo", FileSendPlan.networkFileName("..."))
    assertEquals("archivo", FileSendPlan.networkFileName("  "))
  }

  @Test
  fun `el plan usa el nombre saneado para la red`() {
    val target = file("raro.bin", 4)
    val plan = FileSendPlan.plan(target.absolutePath, "informe?.pdf", "application/pdf")
    assertEquals("informe_.pdf", plan.fileName)
  }
}
