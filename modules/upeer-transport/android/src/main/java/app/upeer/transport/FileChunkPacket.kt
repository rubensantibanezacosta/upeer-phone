package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONObject

internal object FileChunkPacket {
  fun build(
    fileId: String,
    plan: FileSendPlan.Plan,
    index: Int,
    key: ByteArray,
    crypto: CryptoIdentity,
  ): JSONObject? {
    val plain = FileSendPlan.readChunk(plan.file, index, plan.chunkSize, plan.fileSize) ?: return null
    val hash = FileSendPlan.sha256(plain)
    val sealed = try {
      crypto.encryptFileChunk(key, plain)
    } finally {
      plain.fill(0)
    }
    if (sealed == null) return null
    return JSONObject()
      .put("type", "FILE_CHUNK")
      .put("fileId", fileId)
      .put("chunkIndex", index)
      .put("chunkHash", hash)
      .put("data", sealed.getData())
      .put("iv", sealed.getIV())
      .put("tag", sealed.getTag())
  }
}
