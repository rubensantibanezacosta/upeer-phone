package app.upeer.transport

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

internal object FileSendPlan {
  const val MAX_CHUNK_SIZE = 64 * 1024
  const val MAX_FILE_SIZE = 10L * 1024L * 1024L * 1024L

  data class Plan(
    val file: File,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val chunkSize: Int,
    val totalChunks: Int,
    val fileHash: String,
  )

  fun totalChunks(fileSize: Long, chunkSize: Int): Int {
    require(chunkSize in 1..MAX_CHUNK_SIZE) { "Tamaño de trozo no válido" }
    if (fileSize <= 0L) return 0
    return Math.ceil(fileSize.toDouble() / chunkSize.toDouble()).toInt()
  }

  fun plan(
    path: String,
    fileName: String,
    mimeType: String,
    chunkSize: Int = MAX_CHUNK_SIZE,
  ): Plan {
    val file = File(path)
    require(file.isFile) { "El archivo no existe" }
    require(file.canRead()) { "El archivo no se puede leer" }
    val size = file.length()
    require(size in 0..MAX_FILE_SIZE) { "Tamaño de archivo no válido" }
    val safeName = networkFileName(FileReceivePolicy.safeFileName(fileName.ifBlank { file.name }))
    val total = totalChunks(size, chunkSize)
    return Plan(
      file = file,
      fileName = safeName,
      fileSize = size,
      mimeType = mimeType.ifBlank { "application/octet-stream" }.take(160),
      chunkSize = chunkSize,
      totalChunks = total,
      fileHash = sha256(file, chunkSize, size),
    )
  }

  fun networkFileName(name: String): String {
    val cleaned = name.replace("..", "_").map { if (it in FORBIDDEN_CHARS) '_' else it }.joinToString("")
    val trimmed = cleaned.trim().trim('.')
    val usable = if (trimmed.any { it.isLetterOrDigit() }) trimmed else "archivo"
    return usable.take(FileReceivePolicy.MAX_NAME_LENGTH)
  }

  fun readChunk(file: File, index: Int, chunkSize: Int, fileSize: Long): ByteArray? {
    if (index < 0 || chunkSize <= 0) return null
    val offset = index.toLong() * chunkSize.toLong()
    if (offset >= fileSize) return null
    val length = minOf(chunkSize.toLong(), fileSize - offset).toInt()
    if (length <= 0) return null
    RandomAccessFile(file, "r").use { handle ->
      handle.seek(offset)
      val buffer = ByteArray(length)
      var read = 0
      while (read < length) {
        val chunk = handle.read(buffer, read, length - read)
        if (chunk <= 0) break
        read += chunk
      }
      return if (read == length) buffer else buffer.copyOf(read)
    }
  }

  fun sha256(data: ByteArray): String = digest().digest(data).toHex()

  fun sha256(file: File, chunkSize: Int, fileSize: Long): String {
    val digest = digest()
    var index = 0
    while (index * chunkSize.toLong() < fileSize) {
      val chunk = readChunk(file, index, chunkSize, fileSize) ?: break
      digest.update(chunk)
      index++
    }
    return digest.digest().toHex()
  }

  private fun digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

  private fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (byte in this) out.append(HEX[(byte.toInt() shr 4) and 0x0f]).append(HEX[byte.toInt() and 0x0f])
    return out.toString()
  }

  private const val HEX = "0123456789abcdef"
  private val FORBIDDEN_CHARS = charArrayOf('<', '>', ':', '"', '|', '?', '*')
}
