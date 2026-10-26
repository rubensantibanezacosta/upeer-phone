package app.upeer.transport

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class FileTransferState(
  private val storageDir: () -> File,
) {
  internal class Entry(
    val proposal: FileReceivePolicy.Proposal,
    val senderUpeerId: String,
    val assembler: FileChunkAssembler,
    var key: ByteArray?,
    val file: File,
  )

  private val entries = ConcurrentHashMap<String, Entry>()
  private val keys = ConcurrentHashMap<String, ByteArray>()

  fun acceptKey(fileId: String, key: ByteArray): Boolean {
    if (key.size != FILE_KEY_SIZE) return false
    val previous = keys.put(fileId, key)
    if (previous != null && !previous.contentEquals(key)) previous.fill(0)
    entries[fileId]?.key = key
    return true
  }

  fun keyFor(fileId: String): ByteArray? = entries[fileId]?.key ?: keys[fileId]

  fun dropKey(fileId: String) {
    keys.remove(fileId)?.fill(0)
    entries[fileId]?.key = null
  }

  fun open(proposal: FileReceivePolicy.Proposal, senderUpeerId: String): Entry? {
    entries[proposal.fileId]?.let { return it }
    val target = File(transferDir(proposal.chatUpeerId), proposal.fileId)
    return try {
      val entry = Entry(proposal, senderUpeerId, FileChunkAssembler(proposal, target), keys[proposal.fileId], target)
      entries[proposal.fileId] = entry
      entry
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo preparar la transferencia ${proposal.fileId}: ${message(cause)}")
      null
    }
  }

  fun find(fileId: String): Entry? = entries[fileId]

  fun complete(fileId: String): Entry? {
    val entry = entries.remove(fileId) ?: return null
    dropKey(fileId)
    return entry
  }

  fun cancel(fileId: String) {
    val entry = entries.remove(fileId) ?: return
    entry.assembler.close()
    if (entry.file.exists()) entry.file.delete()
    dropKey(fileId)
  }

  fun attachmentsJSON(): String {
    val array = org.json.JSONArray()
    for (entry in entries.values) {
      array.put(
        org.json.JSONObject()
          .put("fileId", entry.proposal.fileId)
          .put("upeerId", entry.senderUpeerId)
          .put("fileName", entry.proposal.fileName)
          .put("fileSize", entry.proposal.fileSize)
          .put("mimeType", entry.proposal.mimeType)
          .put("path", entry.file.absolutePath)
          .put("progress", entry.assembler.progress()),
      )
    }
    return array.toString()
  }

  private fun transferDir(chatUpeerId: String?): File {
    val chat = chatUpeerId?.takeIf { it.startsWith("grp-") } ?: "direct"
    val root = File(storageDir(), "attachments")
    val dir = File(root, chat)
    dir.mkdirs()
    return dir
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  companion object {
    const val FILE_KEY_SIZE = 32
    private const val TAG = "UPeerTransport"
  }
}
