package app.upeer.transport

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Collections

class FileOutboundGroupTest {
  private val sent: MutableList<Pair<String, JSONObject>> = Collections.synchronizedList(mutableListOf())
  private val stored: MutableMap<String, JSONObject> = Collections.synchronizedMap(linkedMapOf())
  private lateinit var root: File
  private lateinit var payload: File

  private val self = "aa".repeat(32)
  private val memberOne = "b1".repeat(32)
  private val memberTwo = "c2".repeat(32)
  private val groupId = "grp-0123456789abcdef"

  private val messages = object : TransportMessages {
    override fun find(messageId: String): JSONObject? = stored[messageId]

    override fun save(message: JSONObject) {
      stored[message.optString("id")] = message
    }
  }

  private class FakeCrypto : FileSendCrypto {
    private var counter = 0

    override fun newFileKey(): ByteArray = ByteArray(32) { ((counter++ + it) and 0xff).toByte() }

    override fun sealFileKeyFor(fileKey: ByteArray, recipientPublicKeyHex: String): String =
      "sealed:${recipientPublicKeyHex.take(4)}:${fileKey.size}"

    override fun encryptFileChunk(key: ByteArray, plaintext: ByteArray): SealedChunk =
      SealedChunk("data", "iv", "tag")
  }

  private inner class FakeDirectory(private val keys: Map<String, String?>) : FileSendDirectory {
    override fun publicKeyFor(upeerId: String): String? = keys[upeerId]
    override fun devicePublicKeyFor(upeerId: String): String? = null
  }

  @Before
  fun setUp() {
    root = File.createTempFile("outbound-group", "")
    root.delete()
    root.mkdirs()
    payload = File(root, "informe.pdf")
    payload.writeBytes(ByteArray(1024) { (it % 97).toByte() })
  }

  @After
  fun tearDown() {
    root.deleteRecursively()
  }

  private fun outbound(
    groups: (String) -> List<String>? = { null },
    directory: FileSendDirectory = FakeDirectory(
      mapOf(memberOne to "11".repeat(32), memberTwo to "22".repeat(32)),
    ),
  ) = FileOutbound(
    accountCrypto = { FakeCrypto() },
    deviceCrypto = { null },
    directory = directory,
    messages = messages,
    send = { target, data, _ ->
      sent += target to JSONObject(data.toString())
      true
    },
    groupMembers = groups,
    selfUpeerId = { self },
  )

  private fun request(upeerId: String) = FileOutbound.Request(
    upeerId = upeerId,
    path = payload.absolutePath,
    fileName = "informe.pdf",
    mimeType = "application/pdf",
    caption = "adjunto",
    isVoiceNote = false,
    chatUpeerId = null,
    messageId = null,
  )

  @Test
  fun `un grupo recibe una propuesta por miembro con el chat del grupo`() {
    val messageId = outbound({ listOf(self, memberOne, memberTwo) }).start(request(groupId))

    val proposals = sent.filter { it.second.optString("type") == "FILE_PROPOSAL" }
    assertEquals(listOf(memberOne, memberTwo), proposals.map { it.first })
    for ((_, proposal) in proposals) {
      assertEquals(groupId, proposal.getString("chatUpeerId"))
      assertEquals(messageId, proposal.getString("messageId"))
    }
    assertNotEquals(
      proposals[0].second.getString("fileId"),
      proposals[1].second.getString("fileId"),
    )
  }

  @Test
  fun `el mensaje local se guarda una sola vez en el grupo`() {
    val messageId = outbound({ listOf(self, memberOne, memberTwo) }).start(request(groupId))

    assertEquals(1, stored.size)
    val message = stored[messageId]!!
    assertEquals(groupId, message.getString("upeerId"))
    assertTrue(message.getBoolean("isMine"))
    assertEquals("pending", message.getString("status"))
    assertEquals("file", JSONObject(message.getString("content")).getString("type"))
  }

  @Test
  fun `no se envia al propio remitente ni se repiten miembros`() {
    outbound({ listOf(self, memberOne, memberOne.uppercase(), memberTwo) }).start(request(groupId))

    val targets = sent.filter { it.second.optString("type") == "FILE_PROPOSAL" }.map { it.first }
    assertEquals(listOf(memberOne, memberTwo), targets)
  }

  @Test
  fun `un miembro sin clave conocida se omite sin romper el envio`() {
    val outbound = outbound(
      groups = { listOf(self, memberOne, memberTwo) },
      directory = FakeDirectory(mapOf(memberOne to "11".repeat(32))),
    )

    outbound.start(request(groupId))

    val targets = sent.filter { it.second.optString("type") == "FILE_PROPOSAL" }.map { it.first }
    assertEquals(listOf(memberOne), targets)
  }

  @Test
  fun `un grupo desconocido no envia nada`() {
    val failure = runCatching { outbound({ null }).start(request(groupId)) }

    assertTrue(failure.isFailure)
    assertTrue(sent.isEmpty())
    assertTrue(stored.isEmpty())
  }

  @Test
  fun `el ACK de un miembro no completa el mensaje del grupo hasta la entrega`() {
    val outbound = outbound({ listOf(self, memberOne, memberTwo) })
    val messageId = outbound.start(request(groupId))
    val proposals = sent.filter { it.second.optString("type") == "FILE_PROPOSAL" }
    val first = proposals[0].second
    val second = proposals[1].second

    outbound.handle(JSONObject().put("type", "FILE_ACCEPT").put("fileId", first.getString("fileId")), memberOne, "FILE_ACCEPT")
    assertEquals("sent", stored[messageId]!!.getString("status"))

    outbound.handle(JSONObject().put("type", "FILE_DONE_ACK").put("fileId", first.getString("fileId")), memberOne, "FILE_DONE_ACK")
    assertEquals("delivered", stored[messageId]!!.getString("status"))

    outbound.handle(JSONObject().put("type", "FILE_CANCEL").put("fileId", second.getString("fileId")), memberTwo, "FILE_CANCEL")
    assertEquals("delivered", stored[messageId]!!.getString("status"))
  }

  @Test
  fun `un ACK de otro contacto no se aplica a la transferencia del miembro`() {
    val outbound = outbound({ listOf(self, memberOne, memberTwo) })
    outbound.start(request(groupId))
    val first = sent.first { it.second.optString("type") == "FILE_PROPOSAL" }.second

    val applied = outbound.handle(
      JSONObject().put("type", "FILE_ACCEPT").put("fileId", first.getString("fileId")),
      memberTwo,
      "FILE_ACCEPT",
    )

    assertFalse(applied)
  }

  @Test
  fun `cancelar una transferencia del grupo cancela a todos los miembros`() {
    val outbound = outbound({ listOf(self, memberOne, memberTwo) })
    outbound.start(request(groupId))
    val first = sent.first { it.second.optString("type") == "FILE_PROPOSAL" }.second.getString("fileId")

    assertTrue(outbound.cancel(first))

    val cancels = sent.filter { it.second.optString("type") == "FILE_CANCEL" }
    assertEquals(2, cancels.size)
    assertEquals("[]", outbound.sendingJSON())
  }

  @Test
  fun `un chat directo sigue guardando el mensaje en el contacto`() {
    val outbound = outbound()
    val messageId = outbound.start(request(memberOne))

    assertEquals(memberOne, stored[messageId]!!.getString("upeerId"))
    assertFalse(sent.first { it.second.optString("type") == "FILE_PROPOSAL" }.second.has("chatUpeerId"))
  }
}
