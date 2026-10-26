package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Collections

class FileOutboundTest {
  private val sent: MutableList<JSONObject> = Collections.synchronizedList(mutableListOf())
  private val labels: MutableList<String> = Collections.synchronizedList(mutableListOf())
  private val stored: MutableMap<String, JSONObject> = Collections.synchronizedMap(linkedMapOf())
  private var withDeviceKey = true
  private var issuedKey: ByteArray? = null
  private lateinit var file: File
  private lateinit var sender: FileOutbound

  private val crypto = object : FileSendCrypto {
    override fun newFileKey(): ByteArray =
      ByteArray(FileTransferState.FILE_KEY_SIZE) { (it + 1).toByte() }.also { issuedKey = it }

    override fun sealFileKeyFor(fileKey: ByteArray, recipientPublicKeyHex: String): String =
      "sealed:$recipientPublicKeyHex"

    override fun encryptFileChunk(key: ByteArray, plaintext: ByteArray): SealedChunk =
      SealedChunk(FileSendPlan.sha256(plaintext), "0".repeat(24), "1".repeat(32))
  }

  private val directory = object : FileSendDirectory {
    override fun publicKeyFor(upeerId: String): String? =
      if (upeerId == PEER) ACCOUNT_KEY else null

    override fun devicePublicKeyFor(upeerId: String): String? = DEVICE_KEY.takeIf { withDeviceKey }
  }

  private val messages = object : FileSendMessages {
    override fun find(messageId: String): JSONObject? = stored[messageId]

    override fun save(message: JSONObject) {
      stored[message.optString("id")] = message
    }
  }

  @Before
  fun setUp() {
    file = File.createTempFile("outbound", ".bin")
    file.writeBytes(ByteArray(150_000) { (it % 251).toByte() })
    sender = FileOutbound({ crypto }, { crypto }, directory, messages, ::record)
  }

  @After
  fun tearDown() {
    file.delete()
  }

  @Test
  fun `la propuesta lleva las dos envolturas de clave`() {
    sender.start(request())

    val proposal = packet("FILE_PROPOSAL")!!
    assertEquals("sealed:$ACCOUNT_KEY", proposal.getString("encryptedKey"))
    assertEquals("sealed:$DEVICE_KEY", proposal.getString("deviceEncryptedKey"))
    assertEquals(3, proposal.getInt("totalChunks"))
    assertEquals(65_536, proposal.getInt("chunkSize"))
    assertEquals(file.length(), proposal.getLong("fileSize"))
    assertEquals(FileSendPlan.sha256(file.readBytes()), proposal.getString("fileHash"))
  }

  @Test
  fun `sin clave de dispositivo solo se envia la envoltura de cuenta`() {
    withDeviceKey = false
    sender.start(request())

    val proposal = packet("FILE_PROPOSAL")!!
    assertTrue(proposal.has("encryptedKey"))
    assertFalse(proposal.has("deviceEncryptedKey"))
  }

  @Test
  fun `un contacto desconocido no inicia la transferencia`() {
    val failure = runCatching { sender.start(request(upeerId = "upeer-desconocido")) }.exceptionOrNull()
    assertTrue(failure is IllegalStateException)
    assertTrue(sent.isEmpty())
  }

  @Test
  fun `un archivo vacio se rechaza`() {
    file.writeBytes(ByteArray(0))
    val failure = runCatching { sender.start(request()) }.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException)
    assertTrue(sent.isEmpty())
  }

  @Test
  fun `tras el acuse llegan los trozos y el cierre`() {
    val messageId = sender.start(request())
    assertEquals("pending", status(messageId))

    accept()
    ackChunks()

    val chunks = synchronized(sent) { sent.filter { it.optString("type") == "FILE_CHUNK" } }
    assertEquals(listOf(0, 1, 2), chunks.map { it.getInt("chunkIndex") }.distinct().sorted())
    assertEquals("sent", status(messageId))
  }

  @Test
  fun `los trozos van cifrados y con el hash del texto plano`() {
    sender.start(request())
    accept()
    ackChunks()

    val chunks = synchronized(sent) { sent.filter { it.optString("type") == "FILE_CHUNK" } }
    for (chunk in chunks) {
      assertTrue(chunk.getString("data").isNotEmpty())
      assertEquals(24, chunk.getString("iv").length)
      assertEquals(32, chunk.getString("tag").length)
      assertTrue(chunk.getString("chunkHash").isNotEmpty())
    }
  }

  @Test
  fun `el acuse final marca el mensaje como entregado`() {
    val messageId = sender.start(request())
    accept()
    ackChunks()

    assertTrue(sender.handle(JSONObject().put("fileId", fileId()), PEER, "FILE_DONE_ACK"))
    assertEquals("delivered", status(messageId))
    assertTrue(issuedKey!!.all { it == 0.toByte() })
  }

  @Test
  fun `un acuse final de otro contacto se ignora`() {
    sender.start(request())
    val packet = JSONObject().put("fileId", fileId())
    assertFalse(sender.handle(packet, "otro", "FILE_DONE_ACK"))
    assertEquals("pending", status(fileId()))
  }

  @Test
  fun `sin acuses se reenvia el trozo pendiente`() {
    sender.start(request())
    accept()
    awaitLabel("FILE_CHUNK")

    val initial = synchronized(sent) { sent.count { it.optString("type") == "FILE_CHUNK" } }
    val deadline = System.currentTimeMillis() + 3_000
    while (System.currentTimeMillis() < deadline) {
      val count = synchronized(sent) { sent.count { it.optString("type") == "FILE_CHUNK" } }
      if (count > initial) return
      Thread.sleep(50)
    }
    throw AssertionError("No hubo retransmision tras $initial trozos")
  }

  @Test
  fun `la cancelacion del destinatario cierra la transferencia y borra la clave`() {
    val messageId = sender.start(request())
    assertTrue(sender.handle(JSONObject().put("fileId", fileId()), PEER, "FILE_CANCEL"))
    assertEquals("failed", status(messageId))
    assertTrue(issuedKey!!.all { it == 0.toByte() })
    assertNotNull(packet("FILE_CANCEL"))
  }

  @Test
  fun `el estado refleja el progreso de la ventana`() {
    sender.start(request())
    accept()
    ackChunks()

    val report = JSONArray(sender.sendingJSON()).getJSONObject(0)
    assertEquals(3, report.getJSONObject("progress").getInt("total"))
    assertEquals(3, report.getJSONObject("progress").getInt("acked"))
    assertEquals("archivo.bin", report.getString("fileName"))
    assertTrue(report.getString("path").isNotEmpty())
  }

  private fun request(upeerId: String = PEER) = FileOutbound.Request(
    upeerId = upeerId,
    path = file.absolutePath,
    fileName = "archivo.bin",
    mimeType = "application/octet-stream",
    caption = null,
    isVoiceNote = false,
    chatUpeerId = null,
    messageId = null,
  )

  private fun accept() {
    assertTrue(sender.handle(JSONObject().put("fileId", fileId()), PEER, "FILE_ACCEPT"))
  }

  private fun ackChunks() {
    val acked = mutableSetOf<Int>()
    val deadline = System.currentTimeMillis() + 8_000
    while (System.currentTimeMillis() < deadline) {
      if (labels.contains("FILE_DONE")) return
      val fresh = synchronized(sent) {
        sent.filter { it.optString("type") == "FILE_CHUNK" }.map { it.getInt("chunkIndex") }
      }.filter { acked.add(it) }
      for (index in fresh) {
        sender.handle(JSONObject().put("fileId", fileId()).put("chunkIndex", index), PEER, "FILE_ACK")
      }
      Thread.sleep(20)
    }
    throw AssertionError("No llego FILE_DONE; enviados: $labels")
  }

  private fun fileId(): String = packet("FILE_PROPOSAL")!!.getString("fileId")

  private fun packet(type: String): JSONObject? =
    synchronized(sent) { sent.firstOrNull { it.optString("type") == type } }

  private fun status(messageId: String): String? = stored[messageId]?.optString("status")

  private fun record(upeerId: String, data: JSONObject, label: String): Boolean {
    labels += label
    sent += JSONObject(data.toString())
    return true
  }

  private fun awaitLabel(label: String) {
    val deadline = System.currentTimeMillis() + 5_000
    while (System.currentTimeMillis() < deadline) {
      if (labels.contains(label)) return
      Thread.sleep(20)
    }
    throw AssertionError("No se envio $label; enviados: $labels")
  }

  private companion object {
    private const val PEER = "upeer-abc123"
    private const val ACCOUNT_KEY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private const val DEVICE_KEY = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  }
}
