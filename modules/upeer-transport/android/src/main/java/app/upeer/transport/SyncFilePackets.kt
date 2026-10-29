package app.upeer.transport

import org.json.JSONObject

internal object SyncFilePackets {
  const val REQ = "SYNC_FILE_REQ"
  const val RES = "SYNC_FILE_RES"
  const val CHUNK = "SYNC_FILE_CHUNK"

  const val CHUNK_SIZE = 48 * 1024
  const val MAX_BYTES = 512L * 1024L * 1024L

  private val types = setOf(REQ, RES, CHUNK)

  fun isFileType(type: String): Boolean = type in types

  fun request(sender: SyncSender, recipientDeviceId: String, requestId: String, fileId: String): JSONObject? {
    if (!SyncPackets.validRequestId(requestId)) return null
    if (fileId.isBlank() || fileId.length > 128) return null
    if (!fileId.all(::safeFileIdChar)) return null
    return SyncPackets.decorate(
      JSONObject().put("type", REQ).put("requestId", requestId).put("fileId", fileId),
      sender,
      recipientDeviceId,
    )
  }

  fun response(
    sender: SyncSender,
    recipientDeviceId: String,
    requestId: String,
    fileId: String,
    meta: ResMeta?,
    reason: String? = null,
  ): JSONObject? {
    if (!SyncPackets.validRequestId(requestId)) return null
    val packet = JSONObject()
      .put("type", RES)
      .put("requestId", requestId)
      .put("fileId", fileId)
      .put("ok", meta != null)
    if (meta == null) {
      packet.put("reason", (reason ?: "unavailable").take(120))
    } else {
      packet
        .put("fileName", meta.fileName)
        .put("fileSize", meta.fileSize)
        .put("chunkSize", meta.chunkSize)
        .put("totalChunks", meta.totalChunks)
    }
    return SyncPackets.decorate(packet, sender, recipientDeviceId)
  }

  fun chunk(
    sender: SyncSender,
    recipientDeviceId: String,
    requestId: String,
    index: Int,
    data: ByteArray,
  ): JSONObject? {
    if (!SyncPackets.validRequestId(requestId)) return null
    if (index < 0) return null
    if (data.isEmpty() || data.size > CHUNK_SIZE) return null
    val encoded = data.joinToString("") { byte -> "%02x".format(byte) }
    return SyncPackets.decorate(
      JSONObject().put("type", CHUNK).put("requestId", requestId).put("index", index).put("data", encoded),
      sender,
      recipientDeviceId,
    )
  }

  fun parseRequest(payload: JSONObject): Req? {
    val requestId = payload.optString("requestId")
    if (!SyncPackets.validRequestId(requestId)) return null
    val fileId = payload.optString("fileId")
    if (!validFileId(fileId)) return null
    return Req(requestId, fileId)
  }

  fun parseResponse(payload: JSONObject): Res? {
    val requestId = payload.optString("requestId")
    if (!SyncPackets.validRequestId(requestId)) return null
    val fileId = payload.optString("fileId")
    if (!validFileId(fileId)) return null
    if (!payload.has("ok")) return null
    if (!payload.optBoolean("ok", false)) {
      return Res(requestId, fileId, null, payload.optString("reason").ifEmpty { "unavailable" })
    }
    val fileName = payload.optString("fileName")
    if (fileName.isEmpty() || fileName.length > 255) return null
    val fileSize = payload.optLong("fileSize", -1L)
    val chunkSize = payload.optInt("chunkSize", 0)
    val totalChunks = payload.optInt("totalChunks", 0)
    if (fileSize <= 0L || fileSize > MAX_BYTES) return null
    if (chunkSize <= 0 || chunkSize > CHUNK_SIZE) return null
    if (totalChunks <= 0) return null
    return Res(requestId, fileId, ResMeta(fileName, fileSize, chunkSize, totalChunks), null)
  }

  fun parseChunk(payload: JSONObject): Chunk? {
    val requestId = payload.optString("requestId")
    if (!SyncPackets.validRequestId(requestId)) return null
    val index = payload.optInt("index", -1)
    if (index < 0) return null
    val encoded = payload.optString("data")
    if (encoded.isEmpty() || encoded.length > CHUNK_SIZE * 2) return null
    if (encoded.length % 2 != 0 || !encoded.all(::isHexChar)) return null
    val bytes = ByteArray(encoded.length / 2) { position ->
      encoded.substring(position * 2, position * 2 + 2).toInt(16).toByte()
    }
    if (bytes.isEmpty() || bytes.size > CHUNK_SIZE) return null
    return Chunk(requestId, index, bytes)
  }

  fun chunkCount(fileSize: Long, chunkSize: Int): Int {
    if (fileSize <= 0L || chunkSize <= 0) return 0
    return ((fileSize + chunkSize - 1) / chunkSize).toInt()
  }

  fun bounds(index: Int, chunkSize: Int, fileSize: Long): Pair<Long, Int>? {
    if (index < 0 || chunkSize <= 0 || fileSize <= 0L) return null
    val start = index.toLong() * chunkSize
    if (start >= fileSize) return null
    return start to minOf(chunkSize.toLong(), fileSize - start).toInt()
  }

  private fun validFileId(value: String): Boolean =
    value.isNotEmpty() && value.length <= 128 && value.all(::safeFileIdChar)

  private fun safeFileIdChar(char: Char): Boolean =
    char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '-' || char == '_' || char == '.'

  private fun isHexChar(char: Char): Boolean =
    char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

  internal data class Req(val requestId: String, val fileId: String)

  internal data class ResMeta(val fileName: String, val fileSize: Long, val chunkSize: Int, val totalChunks: Int)

  internal data class Res(val requestId: String, val fileId: String, val meta: ResMeta?, val reason: String?)

  internal data class Chunk(val requestId: String, val index: Int, val bytes: ByteArray)
}
