package app.upeer.transport

internal object ChatSendPolicy {
  const val MAX_MESSAGE_BYTES = 256 * 1024

  private val ID_PATTERN = Regex("^[0-9a-fA-F]{64}$")

  fun text(upeerId: String, content: String, replyTo: String): String {
    require(ID_PATTERN.matches(upeerId)) { "uPeer ID no válido" }
    return body(content, replyTo)
  }

  fun groupText(groupId: String, content: String, replyTo: String): String {
    require(ProtocolGroups.validId(groupId)) { "El identificador de grupo no es válido" }
    return body(content, replyTo)
  }

  private fun body(content: String, replyTo: String): String {
    require(replyTo.isEmpty() || ReplayPolicy.validMessageID(replyTo)) { "El mensaje citado no es válido" }
    val text = content.trim()
    require(text.isNotEmpty()) { "El mensaje está vacío" }
    require(text.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES) { "El mensaje es demasiado grande" }
    return text
  }
}
