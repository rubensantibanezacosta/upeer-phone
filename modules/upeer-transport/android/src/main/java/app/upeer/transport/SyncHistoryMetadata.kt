package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object SyncHistoryMetadata {
  fun groupEntries(
    deviceId: String,
    groups: JSONArray,
    now: Long,
    sign: (SyncEventRecord) -> SyncOriginSignature?,
    limit: Int,
  ): List<JSONObject> {
    val result = ArrayList<JSONObject>()
    for (index in 0 until groups.length()) {
      if (result.size >= limit) break
      val group = groups.optJSONObject(index) ?: continue
      val groupId = group.optString("groupId")
      if (groupId.isEmpty()) continue
      val eventId = "group-$groupId"
      val record = SyncEventRecord(
        eventId = eventId,
        kind = SyncEventKinds.GROUP_UPSERT,
        occurredAt = now,
        sequence = now,
        payload = group,
        direction = "outbound",
        originDeviceId = deviceId,
        originSignature = null,
        originCertificate = null,
      )
      val origin = sign(record) ?: continue
      result += JSONObject()
        .put("eventId", eventId)
        .put("kind", record.kind)
        .put("occurredAt", now)
        .put("sequence", now)
        .put("payload", group)
        .put("originDeviceId", origin.deviceId)
        .put("originSignature", origin.signature)
        .put("originCertificate", origin.certificate)
    }
    return result
  }
}
