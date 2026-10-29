package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolMessageListAttachmentTest {
  private val peer = "cc".repeat(32)
  private val state = JSONObject().put("messages", JSONArray())
  private val messages = ProtocolMessageList({ state }, Any(), {})

  @Test
  fun `devuelve los adjuntos con su ruta guardada`() {
    save("id-1", "uno", savedPath = "/tmp/uno.png")
    save("id-2", "dos", savedPath = null)

    val refs = messages.attachmentRefs()

    val byId = refs.associateBy { it.fileId }

    assertEquals(2, refs.size)
    assertEquals("/tmp/uno.png", byId["uno"]?.savedPath)
    assertNull(byId["dos"]?.savedPath)
  }

  @Test
  fun `no repite un adjunto que aparece en varios mensajes`() {
    save("id-1", "uno", savedPath = null)
    save("id-2", "uno", savedPath = "/tmp/uno.png")

    assertEquals(1, messages.attachmentRefs().size)
  }

  @Test
  fun `ignora los mensajes de texto y los borrados`() {
    messages.save(
      JSONObject()
        .put("id", "id-texto")
        .put("upeerId", peer)
        .put("isMine", false)
        .put("content", "hola")
        .put("timestamp", 1L),
    )
    save("id-3", "tres", savedPath = null)
    messages.delete("id-3", peer)

    val refs = messages.attachmentRefs()

    assertTrue(refs.isEmpty())
  }

  @Test
  fun `incluye los adjuntos con el tipo antiguo de imagen`() {
    messages.save(
      JSONObject()
        .put("id", "id-img")
        .put("upeerId", peer)
        .put("isMine", false)
        .put("content", JSONObject().put("type", "image").put("fileId", "viejo").put("fileName", "viejo.png").toString())
        .put("timestamp", 1L),
    )

    val refs = messages.attachmentRefs()

    assertEquals(1, refs.size)
    assertEquals("viejo", refs.first().fileId)
  }

  @Test
  fun `reenlaza la ruta guardada con la copia sincronizada`() {
    save("id-1", "uno", savedPath = "/home/otro-dispositivo/uno.png")

    val fixed = messages.repairAttachmentPaths { fileId -> if (fileId == "uno") "/datos/synced/uno.png" else null }

    assertEquals(1, fixed)
    val refs = messages.attachmentRefs()
    assertEquals("/datos/synced/uno.png", refs.first().savedPath)
  }

  private fun save(id: String, fileId: String, savedPath: String?) {
    val content = JSONObject()
      .put("type", "file")
      .put("fileId", fileId)
      .put("fileName", "$fileId.png")
      .put("fileSize", 10)
    if (savedPath != null) content.put("savedPath", savedPath)
    messages.save(
      JSONObject()
        .put("id", id)
        .put("upeerId", peer)
        .put("isMine", false)
        .put("content", content.toString())
        .put("timestamp", 1L),
    )
  }
}
