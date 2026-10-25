package app.upeer.transport

import org.json.JSONObject

internal object FileReceivePolicy {
  const val MAX_CHUNK_SIZE = 64 * 1024
  const val MAX_FILE_SIZE = 10L * 1024L * 1024L * 1024L
  const val MAX_NAME_LENGTH = 255
  const val MAX_CAPTION_LENGTH = 512

  private val HASH_PATTERN = Regex("^[0-9a-fA-F]{64}$")

  data class Proposal(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val totalChunks: Int,
    val chunkSize: Int,
    val mimeType: String,
    val fileHash: String?,
    val caption: String?,
    val isVoiceNote: Boolean,
    val chatUpeerId: String?,
    val messageId: String?
  )

  fun proposal(json: JSONObject): Proposal {
    val fileId = json.optString("fileId", "")
    require(fileId.isNotEmpty() && fileId.length <= 128) { "fileId no válido" }

    val fileName = safeFileName(json.optString("fileName", ""))
    val fileSize = json.optLong("fileSize", -1L)
    require(fileSize in 0..MAX_FILE_SIZE) { "Tamaño de archivo no válido" }

    val chunkSize = json.optInt("chunkSize", 0)
    require(chunkSize in 1..MAX_CHUNK_SIZE) { "Tamaño de trozo no válido" }

    val totalChunks = json.optInt("totalChunks", -1)
    require(totalChunks >= 0) { "Número de trozos no válido" }
    if (fileSize == 0L) {
      require(totalChunks == 0) { "Un archivo vacío no puede tener trozos" }
    } else {
      require(totalChunks > 0) { "Un archivo con contenido necesita trozos" }
      require(Math.ceil(fileSize.toDouble() / chunkSize.toDouble()).toInt() == totalChunks) {
        "El número de trozos no cuadra con el tamaño"
      }
    }

    val fileHash = json.optString("fileHash", "")
    require(fileHash.isEmpty() || HASH_PATTERN.matches(fileHash)) { "Hash de archivo no válido" }

    val chatUpeerId = json.optString("chatUpeerId", "")
    require(chatUpeerId.isEmpty() || (chatUpeerId.startsWith("grp-") && chatUpeerId.length <= 128)) {
      "Conversación de grupo no válida"
    }

    val messageId = json.optString("messageId", "")
    require(messageId.length <= 100) { "Identificador de mensaje no válido" }

    val mimeType = json.optString("mimeType", "").ifEmpty { "application/octet-stream" }
    require(mimeType.length <= 160) { "Tipo MIME no válido" }

    val caption = json.optString("caption", "").take(MAX_CAPTION_LENGTH).ifEmpty { null }

    return Proposal(
      fileId = fileId,
      fileName = fileName,
      fileSize = fileSize,
      totalChunks = totalChunks,
      chunkSize = chunkSize,
      mimeType = mimeType,
      fileHash = fileHash.ifEmpty { null },
      caption = caption,
      isVoiceNote = json.optBoolean("isVoiceNote", false),
      chatUpeerId = chatUpeerId.ifEmpty { null },
      messageId = messageId.ifEmpty { null }
    )
  }

  fun safeFileName(raw: String): String {
    val trimmed = raw.trim().replace('\\', '/')
    val base = trimmed.substringAfterLast('/')
    val cleaned = base.filter { it.code >= 0x20 && it != '\u007f' }
    require(cleaned.isNotEmpty() && cleaned != "." && cleaned != "..") { "Nombre de archivo no válido" }
    require(cleaned.length <= MAX_NAME_LENGTH) { "Nombre de archivo demasiado largo" }
    return cleaned
  }
}
