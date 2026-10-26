package app.upeer.transport

import org.json.JSONObject

internal object FileOutboundMessage {
  fun content(plan: FileSendPlan.Plan, proposal: JSONObject, savedPath: String?): String {
    val payload = JSONObject()
      .put("type", "file")
      .put("transferId", proposal.optString("fileId"))
      .put("fileId", proposal.optString("fileId"))
      .put("fileName", plan.fileName)
      .put("fileSize", plan.fileSize)
      .put("mimeType", plan.mimeType)
    savedPath?.let { payload.put("savedPath", it) }
    proposal.optString("caption").takeIf { it.isNotEmpty() }?.let { payload.put("caption", it) }
    if (proposal.optBoolean("isVoiceNote")) payload.put("isVoiceNote", true)
    return payload.toString()
  }

  fun save(
    messages: FileSendMessages,
    messageId: String,
    upeerId: String,
    plan: FileSendPlan.Plan,
    proposal: JSONObject,
    status: String,
    savedPath: String?,
  ) {
    val content = content(plan, proposal, savedPath)
    val existing = messages.find(messageId)
    if (existing != null) {
      existing.put("content", content)
      existing.put("status", status)
      messages.save(existing)
      return
    }
    messages.save(
      JSONObject()
        .put("id", messageId)
        .put("upeerId", upeerId)
        .put("isMine", true)
        .put("content", content)
        .put("status", status)
        .put("version", 1)
        .put("isEdited", false)
        .put("timestamp", System.currentTimeMillis()),
    )
  }
}
