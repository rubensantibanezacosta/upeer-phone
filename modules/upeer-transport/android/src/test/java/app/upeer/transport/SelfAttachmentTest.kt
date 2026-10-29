package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SelfAttachmentTest {
  private val me = "da4163f3528710db1adeca2211274d217728845044c70b912759e856493d1834"

  private fun plan(name: String = "nota.txt", content: String = "hola"): FileSendPlan.Plan {
    val file = File.createTempFile("upeer-self-attachment", "-$name")
    file.writeText(content)
    file.deleteOnExit()
    return FileSendPlan.plan(file.absolutePath, name, "text/plain")
  }

  @Test
  fun `el adjunto propio queda entregado sin pasar por la red`() {
    val message = SelfAttachment.message(
      messageId = "m-1",
      upeerId = me,
      plan = plan(),
      savedPath = "/tmp/nota.txt",
      caption = null,
      isVoiceNote = false,
      timestamp = 1_700_000_000_000L,
    )
    assertEquals("m-1", message.getString("id"))
    assertEquals(me, message.getString("upeerId"))
    assertTrue(message.getBoolean("isMine"))
    assertEquals("read", message.getString("status"))
    assertEquals(1_700_000_000_000L, message.getLong("timestamp"))
  }

  @Test
  fun `el contenido lleva la ruta local para que la vista previa funcione`() {
    val payload = JSONObject(
      SelfAttachment.content("m-1", plan(), "/tmp/nota.txt", null, false),
    )
    assertEquals("file", payload.getString("type"))
    assertEquals("m-1", payload.getString("fileId"))
    assertEquals("m-1", payload.getString("transferId"))
    assertEquals("/tmp/nota.txt", payload.getString("savedPath"))
    assertFalse(payload.has("caption"))
    assertFalse(payload.has("isVoiceNote"))
  }

  @Test
  fun `la nota de voz propia conserva la marca y el pie de foto`() {
    val payload = JSONObject(
      SelfAttachment.content("m-2", plan("voz.m4a"), "/tmp/voz.m4a", "  hola  ", true),
    )
    assertEquals("hola", payload.getString("caption"))
    assertTrue(payload.getBoolean("isVoiceNote"))
  }

  @Test
  fun `el pie de foto se recorta al limite del protocolo`() {
    val largo = "x".repeat(SelfAttachment.MAX_CAPTION_LENGTH + 50)
    val payload = JSONObject(SelfAttachment.content("m-3", plan(), "/tmp/nota.txt", largo, false))
    assertEquals(SelfAttachment.MAX_CAPTION_LENGTH, payload.getString("caption").length)
  }

  @Test
  fun `el pie de foto vacio no se guarda`() {
    val payload = JSONObject(SelfAttachment.content("m-4", plan(), "/tmp/nota.txt", "   ", false))
    assertFalse(payload.has("caption"))
  }

  @Test
  fun `solo el chat propio se trata como adjunto local`() {
    val otro = "a60cc35ee3d4e106f7563d9ba4c2deaad4dc0596ded6acdab53585fba354948b"
    assertTrue(SelfMessage.isSelfChat(me, me))
    assertFalse(SelfMessage.isSelfChat(otro, me))
  }

  @Test
  fun `un archivo que no existe no se puede adjuntar`() {
    val missing = Files.createTempDirectory("upeer-self").resolve("no-existe.txt").toString()
    val error = runCatching { FileSendPlan.plan(missing, "no-existe.txt", "text/plain") }.exceptionOrNull()
    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `el adjunto propio se copia a una carpeta duradera`() {
    val source = File.createTempFile("upeer-self-source", "-foto.png")
    source.writeText("contenido durable")
    source.deleteOnExit()
    val directory = Files.createTempDirectory("upeer-self-store").toFile()

    val stored = SelfAttachment.store(directory, "m-9", source)

    assertTrue(stored.isFile)
    assertEquals(directory.absolutePath, stored.parentFile?.absolutePath)
    assertEquals("contenido durable", stored.readText())
    assertEquals("m-9.png", stored.name)
  }

  @Test
  fun `un archivo sin extension se guarda con el identificador`() {
    val source = File.createTempFile("upeer-self-source", "-README")
    source.writeText("sin extension")
    source.deleteOnExit()
    val directory = Files.createTempDirectory("upeer-self-store").toFile()

    val stored = SelfAttachment.store(directory, "m-10", source)

    assertEquals("m-10", stored.name)
  }

  @Test
  fun `la extension se normaliza a minusculas`() {
    val source = File.createTempFile("upeer-self-source", "-FOTO.PNG")
    source.writeText("mayusculas")
    source.deleteOnExit()
    val directory = Files.createTempDirectory("upeer-self-store").toFile()

    val stored = SelfAttachment.store(directory, "m-11", source)

    assertEquals("m-11.png", stored.name)
  }

  @Test
  fun `una extension demasiado larga no se propaga al nombre guardado`() {
    val source = File.createTempFile("upeer-self-source", "-nota.superlongextension")
    source.writeText("extension larga")
    source.deleteOnExit()
    val directory = Files.createTempDirectory("upeer-self-store").toFile()

    val stored = SelfAttachment.store(directory, "m-12", source)

    assertEquals("m-12", stored.name)
  }
}
