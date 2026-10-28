package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class FileInboundTest {
  private val replies: MutableList<JSONObject> = Collections.synchronizedList(mutableListOf())
  private val labels: MutableList<String> = Collections.synchronizedList(mutableListOf())
  private val stored: MutableMap<String, JSONObject> = Collections.synchronizedMap(linkedMapOf())
  private lateinit var root: File
  private lateinit var transfers: FileTransferState

  private val fileKey = ByteArray(FileTransferState.FILE_KEY_SIZE) { (0x80 + it).toByte() }
  private val content = ByteArray(250) { ((it * 7) % 251).toByte() }

  private inner class FakeCrypto(private val unlocks: Boolean = true) : FileReceiveCrypto {
    override fun openFileKey(sealedHex: String): ByteArray? =
      if (unlocks && (sealedHex == SEALED_ACCOUNT || sealedHex == SEALED_DEVICE)) fileKey.copyOf() else null

    override fun decryptFileChunk(key: ByteArray, ivHex: String, tagHex: String, dataBase64: String): ByteArray {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unhex(ivHex)))
      return cipher.doFinal(Base64.getDecoder().decode(dataBase64) + unhex(tagHex))
    }
  }

  private val messages = object : TransportMessages {
    override fun find(messageId: String): JSONObject? = stored[messageId]

    override fun save(message: JSONObject) {
      stored[message.optString("id")] = message
    }
  }

  @Before
  fun setUp() {
    root = File.createTempFile("inbound", "")
    root.delete()
    root.mkdirs()
    transfers = FileTransferState { root }
  }

  @After
  fun tearDown() {
    root.deleteRecursively()
  }

  private fun inbound(
    account: FileReceiveCrypto? = FakeCrypto(),
    device: FileReceiveCrypto? = FakeCrypto(),
    groups: (String) -> List<String>? = { null },
  ) = FileInbound({ account }, { device }, transfers, messages, ::record, groups)

  @Test
  fun `acepta la propuesta y guarda la etiqueta del mensaje`() {
    inbound().handle(proposal(), PEER, "FILE_PROPOSAL")

    assertEquals(listOf("FILE_ACCEPT"), labels)
    val message = stored[MESSAGE_ID]!!
    assertEquals(PEER, message.getString("upeerId"))
    assertFalse(message.getBoolean("isMine"))
    val content = JSONObject(message.getString("content"))
    assertEquals("file", content.getString("type"))
    assertEquals("adjunto-grande.bin", content.getString("fileName"))
    assertFalse(content.has("savedPath"))
  }

  @Test
  fun `sin clave de cuenta ni de dispositivo no se acepta la propuesta`() {
    inbound(null, null).handle(proposal(), PEER, "FILE_PROPOSAL")

    assertTrue(labels.isEmpty())
    assertTrue(stored.isEmpty())
  }

  @Test
  fun `una clave que no abre no acepta la propuesta`() {
    inbound(FakeCrypto(unlocks = false), FakeCrypto(unlocks = false)).handle(proposal(), PEER, "FILE_PROPOSAL")

    assertTrue(labels.isEmpty())
    assertTrue(stored.isEmpty())
  }

  @Test
  fun `sin sobre de dispositivo se abre con la cuenta`() {
    inbound().handle(proposal().apply { remove("deviceEncryptedKey") }, PEER, "FILE_PROPOSAL")

    assertEquals(listOf("FILE_ACCEPT"), labels)
  }

  @Test
  fun `rechaza propuestas con el nombre fuera de rango`() {
    inbound().handle(proposal().put("fileName", ".."), PEER, "FILE_PROPOSAL")
    inbound().handle(proposal().put("totalChunks", 9), PEER, "FILE_PROPOSAL")
    inbound().handle(proposal().put("chunkSize", 0), PEER, "FILE_PROPOSAL")

    assertTrue(labels.isEmpty())
  }

  @Test
  fun `los tres trozos se acusan y el archivo se guarda con su hash`() {
    receiveAll()

    assertEquals(3, labels.count { it == "FILE_ACK" })
    assertEquals("FILE_DONE_ACK", labels.last())
    val savedPath = JSONObject(stored[MESSAGE_ID]!!.getString("content")).getString("savedPath")
    val saved = File(savedPath)
    assertTrue(saved.exists())
    assertArrayEquals(content, saved.readBytes())
    assertEquals(sha256(content), sha256(saved.readBytes()))
    assertNull(transfers.find(FILE_ID))
  }

  @Test
  fun `un trozo duplicado se acusa otra vez y no se escribe dos veces`() {
    inbound().handle(proposal(), PEER, "FILE_PROPOSAL")
    inbound().handle(chunk(0), PEER, "FILE_CHUNK")
    inbound().handle(chunk(0), PEER, "FILE_CHUNK")

    assertEquals(2, labels.count { it == "FILE_ACK" })
    assertEquals(2, replies.count { it.optString("type") == "FILE_ACK" })
    assertEquals(40, JSONArray(transfers.attachmentsJSON()).getJSONObject(0).getInt("progress"))
  }

  @Test
  fun `un trozo con hash incorrecto no se acusa`() {
    val handler = inbound()
    handler.handle(proposal(), PEER, "FILE_PROPOSAL")
    handler.handle(chunk(0).put("chunkHash", "0".repeat(64)), PEER, "FILE_CHUNK")

    assertTrue(labels.none { it == "FILE_ACK" })
  }

  @Test
  fun `un trozo de otro contacto se ignora`() {
    val handler = inbound()
    handler.handle(proposal(), PEER, "FILE_PROPOSAL")
    handler.handle(chunk(0), "otro-contacto", "FILE_CHUNK")

    assertTrue(labels.none { it == "FILE_ACK" })
  }

  @Test
  fun `un trozo sin propuesta se ignora`() {
    inbound().handle(chunk(0), PEER, "FILE_CHUNK")

    assertTrue(labels.isEmpty())
  }

  @Test
  fun `FILE_DONE con trozos pendientes acusa pero no cierra el archivo`() {
    val handler = inbound()
    handler.handle(proposal(), PEER, "FILE_PROPOSAL")
    handler.handle(chunk(0), PEER, "FILE_CHUNK")
    handler.handle(done(), PEER, "FILE_DONE")

    assertEquals("FILE_DONE_ACK", labels.last())
    assertFalse(JSONObject(stored[MESSAGE_ID]!!.getString("content")).has("savedPath"))
    assertTrue(transfers.find(FILE_ID) != null)
  }

  @Test
  fun `un archivo con hash global distinto no se entrega`() {
    val handler = inbound()
    handler.handle(proposal().put("fileHash", "1".repeat(64)), PEER, "FILE_PROPOSAL")
    for (index in 0 until 3) handler.handle(chunk(index), PEER, "FILE_CHUNK")
    handler.handle(done(), PEER, "FILE_DONE")

    assertFalse(JSONObject(stored[MESSAGE_ID]!!.getString("content")).has("savedPath"))
    assertNull(transfers.find(FILE_ID))
  }

  @Test
  fun `la cancelacion del emisor borra la transferencia`() {
    val handler = inbound()
    handler.handle(proposal(), PEER, "FILE_PROPOSAL")
    handler.handle(chunk(0), PEER, "FILE_CHUNK")
    handler.handle(JSONObject().put("type", "FILE_CANCEL").put("fileId", FILE_ID), PEER, "FILE_CANCEL")

    assertNull(transfers.find(FILE_ID))
    assertTrue(labels.none { it == "FILE_DONE_ACK" })
  }

  @Test
  fun `el progreso visible refleja los trozos recibidos`() {
    val handler = inbound()
    handler.handle(proposal(), PEER, "FILE_PROPOSAL")
    handler.handle(chunk(0), PEER, "FILE_CHUNK")

    val report = JSONArray(transfers.attachmentsJSON()).getJSONObject(0)
    assertEquals(40, report.getInt("progress"))
    assertEquals("adjunto-grande.bin", report.getString("fileName"))
    assertEquals(PEER, report.getString("upeerId"))
  }

  @Test
  fun `un adjunto de grupo se guarda en el chat del grupo`() {
    inbound(groups = { if (it == GROUP_ID) listOf(PEER) else null })
      .handle(proposal().put("chatUpeerId", GROUP_ID), PEER, "FILE_PROPOSAL")

    assertEquals(GROUP_ID, stored[MESSAGE_ID]!!.getString("upeerId"))
    assertEquals(listOf("FILE_ACCEPT"), labels)
  }

  @Test
  fun `un adjunto de grupo con el emisor fuera del grupo se rechaza`() {
    inbound(groups = { if (it == GROUP_ID) listOf("otro-miembro") else null })
      .handle(proposal().put("chatUpeerId", GROUP_ID), PEER, "FILE_PROPOSAL")

    assertTrue(labels.isEmpty())
    assertTrue(stored.isEmpty())
  }

  @Test
  fun `un adjunto para un grupo desconocido se rechaza`() {
    inbound().handle(proposal().put("chatUpeerId", GROUP_ID), PEER, "FILE_PROPOSAL")

    assertTrue(labels.isEmpty())
    assertTrue(stored.isEmpty())
  }

  @Test
  fun `el adjunto del grupo se completa en el chat del grupo`() {
    val handler = inbound(groups = { if (it == GROUP_ID) listOf(PEER) else null })
    handler.handle(proposal().put("chatUpeerId", GROUP_ID), PEER, "FILE_PROPOSAL")
    for (index in 0 until 3) handler.handle(chunk(index), PEER, "FILE_CHUNK")
    handler.handle(done(), PEER, "FILE_DONE")

    val message = stored[MESSAGE_ID]!!
    assertEquals(GROUP_ID, message.getString("upeerId"))
    val savedPath = JSONObject(message.getString("content")).getString("savedPath")
    assertTrue(File(savedPath).exists())
  }

  private fun receiveAll() {
    val handler = inbound()
    handler.handle(proposal(), PEER, "FILE_PROPOSAL")
    for (index in 0 until 3) handler.handle(chunk(index), PEER, "FILE_CHUNK")
    handler.handle(done(), PEER, "FILE_DONE")
  }

  private fun proposal() = JSONObject()
    .put("type", "FILE_PROPOSAL")
    .put("fileId", FILE_ID)
    .put("fileName", "adjunto-grande.bin")
    .put("fileSize", content.size)
    .put("chunkSize", CHUNK_SIZE)
    .put("totalChunks", 3)
    .put("mimeType", "application/octet-stream")
    .put("fileHash", sha256(content))
    .put("messageId", MESSAGE_ID)
    .put("encryptedKey", SEALED_ACCOUNT)
    .put("deviceEncryptedKey", SEALED_DEVICE)

  private fun done() = JSONObject().put("type", "FILE_DONE").put("fileId", FILE_ID)

  private fun chunk(index: Int): JSONObject {
    val start = index * CHUNK_SIZE
    val end = minOf(start + CHUNK_SIZE, content.size)
    val plain = content.copyOfRange(start, end)
    val iv = ByteArray(12) { (0xa0 + index * 16 + it).toByte() }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(fileKey, "AES"), GCMParameterSpec(128, iv))
    val sealed = cipher.doFinal(plain)
    return JSONObject()
      .put("type", "FILE_CHUNK")
      .put("fileId", FILE_ID)
      .put("chunkIndex", index)
      .put("chunkHash", sha256(plain))
      .put("data", Base64.getEncoder().encodeToString(sealed.copyOfRange(0, plain.size)))
      .put("iv", hex(iv))
      .put("tag", hex(sealed.copyOfRange(plain.size, sealed.size)))
  }

  private fun hex(bytes: ByteArray): String {
    val builder = StringBuilder(bytes.size * 2)
    for (byte in bytes) builder.append(String.format("%02x", byte))
    return builder.toString()
  }

  private fun unhex(value: String): ByteArray {
    require(value.length % 2 == 0) { "hexadecimal impar" }
    return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
  }

  private fun sha256(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

  private fun record(upeerId: String, data: JSONObject, label: String): Boolean {
    labels += label
    replies += JSONObject(data.toString())
    return true
  }

  private companion object {
    private const val PEER = "upeer-abc123"
    private const val GROUP_ID = "grp-0123456789abcdef"
    private const val FILE_ID = "6f1c2f5e-6b7a-4c2d-9d1e-2a3b4c5d6e7f"
    private const val MESSAGE_ID = "mensaje-1"
    private const val CHUNK_SIZE = 100
    private const val SEALED_ACCOUNT =
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private const val SEALED_DEVICE =
      "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  }
}
