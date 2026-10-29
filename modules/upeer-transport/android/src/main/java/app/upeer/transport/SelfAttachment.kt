package app.upeer.transport

import org.json.JSONObject
import java.io.File

internal object SelfAttachment {
  const val MAX_CAPTION_LENGTH = FileReceivePolicy.MAX_CAPTION_LENGTH

  fun store(directory: File, messageId: String, source: File): File {
    directory.mkdirs()
    val target = File(directory, storedName(messageId, source.name))
    source.copyTo(target, overwrite = true)
    return target
  }

  private fun storedName(messageId: String, fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    val raw = if (dot >= 0) fileName.substring(dot + 1) else ""
    val extension = raw.filter { it.isLetterOrDigit() }.lowercase()
    return if (extension.isEmpty() || extension.length > 8) messageId else "$messageId.$extension"
  }

  fun content(
    messageId: String,
    plan: FileSendPlan.Plan,
    savedPath: String,
    caption: String?,
    isVoiceNote: Boolean,
  ): String {
    val payload = JSONObject()
      .put("type", "file")
      .put("transferId", messageId)
      .put("fileId", messageId)
      .put("fileName", plan.fileName)
      .put("fileSize", plan.fileSize)
      .put("mimeType", plan.mimeType)
      .put("savedPath", savedPath)
    caption?.trim()?.takeIf { it.isNotEmpty() }
      ?.let { payload.put("caption", it.take(MAX_CAPTION_LENGTH)) }
    if (isVoiceNote) payload.put("isVoiceNote", true)
    return payload.toString()
  }

  fun message(
    messageId: String,
    upeerId: String,
    plan: FileSendPlan.Plan,
    savedPath: String,
    caption: String?,
    isVoiceNote: Boolean,
    timestamp: Long = System.currentTimeMillis(),
  ): JSONObject = JSONObject()
    .put("id", messageId)
    .put("upeerId", upeerId)
    .put("isMine", true)
    .put("content", content(messageId, plan, savedPath, caption, isVoiceNote))
    .put("status", "read")
    .put("version", 1)
    .put("isEdited", false)
    .put("timestamp", timestamp)
}
