package app.upeer.transport

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.BitSet

internal class FileChunkAssembler(
  private val proposal: FileReceivePolicy.Proposal,
  private val target: File
) {
  enum class Outcome { STORED, DUPLICATE, OUT_OF_RANGE, HASH_MISMATCH }

  private val received = BitSet(proposal.totalChunks)
  private var receivedBytes = 0L
  private var stream: RandomAccessFile? = null

  fun accept(index: Int, data: ByteArray, expectedHash: String?): Outcome {
    if (index < 0 || index >= proposal.totalChunks) return Outcome.OUT_OF_RANGE
    val expectedLength = expectedLength(index)
    if (data.isEmpty() || data.size != expectedLength) return Outcome.OUT_OF_RANGE
    if (received[index]) return Outcome.DUPLICATE
    if (expectedHash != null && !expectedHash.equals(sha256(data), ignoreCase = true)) return Outcome.HASH_MISMATCH
    write(index, data)
    received.set(index)
    receivedBytes += data.size.toLong()
    return Outcome.STORED
  }

  fun receivedBytes(): Long = receivedBytes

  fun progress(): Int {
    if (proposal.fileSize <= 0L) return 0
    return ((receivedBytes * 100L) / proposal.fileSize).toInt().coerceIn(0, 100)
  }

  fun isComplete(): Boolean {
    if (proposal.totalChunks == 0) return proposal.fileSize == 0L
    return received.cardinality() == proposal.totalChunks
  }

  fun finish(): File {
    require(isComplete()) { "Faltan trozos por recibir" }
    close()
    require(target.length() == proposal.fileSize) { "El archivo recibido no tiene el tamaño esperado" }
    val expected = proposal.fileHash
    if (expected != null) {
      require(expected.equals(fileSha256(target), ignoreCase = true)) { "El archivo recibido no coincide con su hash" }
    }
    return target
  }

  fun close() {
    val current = stream ?: return
    stream = null
    current.close()
  }

  private fun write(index: Int, data: ByteArray) {
    val handle = open()
    handle.seek(index.toLong() * proposal.chunkSize.toLong())
    handle.write(data)
  }

  private fun open(): RandomAccessFile {
    stream?.let { return it }
    target.parentFile?.mkdirs()
    val handle = RandomAccessFile(target, "rw")
    if (handle.length() != proposal.fileSize) handle.setLength(proposal.fileSize)
    stream = handle
    return handle
  }

  private fun expectedLength(index: Int): Int {
    val offset = index.toLong() * proposal.chunkSize.toLong()
    val remaining = proposal.fileSize - offset
    return minOf(proposal.chunkSize.toLong(), remaining).toInt()
  }

  private fun sha256(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

  private fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return hex(digest.digest())
  }

  private fun hex(bytes: ByteArray): String {
    val builder = StringBuilder(bytes.size * 2)
    for (byte in bytes) builder.append(String.format("%02x", byte))
    return builder.toString()
  }
}
