package app.upeer.transport

import org.json.JSONObject

internal object SyncFilePackets {
  const val REQ = "SYNC_FILE_REQ"
  const val RES = "SYNC_FILE_RES"
  const val CHUNK = "SYNC_FILE_CHUNK"

  const val CHUNK_SIZE = 192 * 1024

  /** Varios tramos por frame: 8 tramos de 192 KB en base64 son ~2 MB, muy por debajo de los 10 MB del puente. */
  const val MAX_BATCH_CHUNKS = 8

  /** Margen de seguridad sobre el JSON codificado de un lote. */
  private const val MAX_BATCH_ENCODED = 2 * 1024 * 1024
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
    val encoded = encodeBase64(data)
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
    if (encoded.isEmpty() || encoded.length > MAX_ENCODED_SIZE) return null
    val bytes = decodeBase64(encoded) ?: return null
    if (bytes.isEmpty() || bytes.size > CHUNK_SIZE) return null
    return Chunk(requestId, index, bytes)
  }

  /**
   * Varios tramos en un solo frame.
   *
   * Cada frame cuesta una vuelta de ratchet y una escritura; con un tramo por frame, servir un vídeo
   * de 140 tramos paga 140 veces ese coste. El tamaño lógico del chunk no cambia: el receptor sigue
   * recibiendo tramos sueltos con su índice y los reensambla igual, con huecos incluidos.
   *
   * El límite lo pone el transporte, no el gusto: el puente rechaza frames de más de 10 MB
   * (`MaxFrameBytes` en `framing.go`). Con tramos de 192 KB en base64 (~256 KB) caben 8 sin acercarse.
   */
  fun chunkBatch(
    sender: SyncSender,
    recipientDeviceId: String,
    requestId: String,
    firstIndex: Int,
    chunks: List<ByteArray>,
  ): JSONObject? {
    if (!SyncPackets.validRequestId(requestId)) return null
    if (firstIndex < 0 || chunks.isEmpty() || chunks.size > MAX_BATCH_CHUNKS) return null
    if (chunks.any { it.isEmpty() || it.size > CHUNK_SIZE }) return null
    val items = org.json.JSONArray()
    var encodedBytes = 0
    for ((offset, data) in chunks.withIndex()) {
      val encoded = encodeBase64(data)
      encodedBytes += encoded.length
      if (encodedBytes > MAX_BATCH_ENCODED) return null
      items.put(
        JSONObject().put("index", firstIndex + offset).put("data", encoded),
      )
    }
    return SyncPackets.decorate(
      JSONObject()
        .put("type", CHUNK)
        .put("requestId", requestId)
        .put("batch", items),
      sender,
      recipientDeviceId,
    )
  }

  /** Deshace un lote en tramos sueltos. Un frame de un solo tramo (`data`) devuelve uno. */
  fun parseChunks(payload: JSONObject): List<Chunk> {
    val batch = payload.optJSONArray("batch")
    if (batch == null) return listOfNotNull(parseChunk(payload))
    val requestId = payload.optString("requestId")
    if (!SyncPackets.validRequestId(requestId)) return emptyList()
    if (batch.length() > MAX_BATCH_CHUNKS) return emptyList()
    val chunks = ArrayList<Chunk>(batch.length())
    var encodedBytes = 0
    for (position in 0 until batch.length()) {
      val item = batch.optJSONObject(position) ?: return emptyList()
      val index = item.optInt("index", -1)
      if (index < 0) return emptyList()
      val encoded = item.optString("data")
      if (encoded.isEmpty()) return emptyList()
      encodedBytes += encoded.length
      if (encodedBytes > MAX_BATCH_ENCODED) return emptyList()
      val bytes = decodeBase64(encoded) ?: return emptyList()
      if (bytes.isEmpty() || bytes.size > CHUNK_SIZE) return emptyList()
      chunks.add(Chunk(requestId, index, bytes))
    }
    return chunks
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

  private fun base64Value(char: Char): Int = when (char) {
    in 'A'..'Z' -> char - 'A'
    in 'a'..'z' -> char - 'a' + 26
    in '0'..'9' -> char - '0' + 52
    '+' -> 62
    '/' -> 63
    else -> -1
  }

  private fun encodeBase64(data: ByteArray): String {
    val out = StringBuilder((data.size + 2) / 3 * 4)
    var position = 0
    while (position + 2 < data.size) {
      val value = ((data[position].toInt() and 0xFF) shl 16) or
        ((data[position + 1].toInt() and 0xFF) shl 8) or
        (data[position + 2].toInt() and 0xFF)
      out.append(BASE64[value ushr 18 and 0x3F])
        .append(BASE64[value ushr 12 and 0x3F])
        .append(BASE64[value ushr 6 and 0x3F])
        .append(BASE64[value and 0x3F])
      position += 3
    }
    val remaining = data.size - position
    if (remaining == 1) {
      val value = (data[position].toInt() and 0xFF) shl 16
      out.append(BASE64[value ushr 18 and 0x3F]).append(BASE64[value ushr 12 and 0x3F]).append("==")
    } else if (remaining == 2) {
      val value = ((data[position].toInt() and 0xFF) shl 16) or ((data[position + 1].toInt() and 0xFF) shl 8)
      out.append(BASE64[value ushr 18 and 0x3F])
        .append(BASE64[value ushr 12 and 0x3F])
        .append(BASE64[value ushr 6 and 0x3F])
        .append('=')
    }
    return out.toString()
  }

  private fun decodeBase64(encoded: String): ByteArray? {
    if (encoded.length % 4 != 0) return null
    val padding = when {
      encoded.endsWith("==") -> 2
      encoded.endsWith("=") -> 1
      else -> 0
    }
    val out = ByteArray(encoded.length / 4 * 3 - padding)
    var source = 0
    var target = 0
    while (source < encoded.length) {
      val first = base64Value(encoded[source])
      val second = base64Value(encoded[source + 1])
      val third = if (encoded[source + 2] == '=') 0 else base64Value(encoded[source + 2])
      val fourth = if (encoded[source + 3] == '=') 0 else base64Value(encoded[source + 3])
      if (first < 0 || second < 0 || third < 0 || fourth < 0) return null
      val value = (first shl 18) or (second shl 12) or (third shl 6) or fourth
      if (target < out.size) out[target++] = (value ushr 16 and 0xFF).toByte()
      if (target < out.size) out[target++] = (value ushr 8 and 0xFF).toByte()
      if (target < out.size) out[target++] = (value and 0xFF).toByte()
      source += 4
    }
    return out
  }

  private const val MAX_ENCODED_SIZE = (CHUNK_SIZE + 2) / 3 * 4

  private val BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()

  internal data class Req(val requestId: String, val fileId: String)

  internal data class ResMeta(val fileName: String, val fileSize: Long, val chunkSize: Int, val totalChunks: Int)

  internal data class Res(val requestId: String, val fileId: String, val meta: ResMeta?, val reason: String?)

  internal data class Chunk(val requestId: String, val index: Int, val bytes: ByteArray)
}
