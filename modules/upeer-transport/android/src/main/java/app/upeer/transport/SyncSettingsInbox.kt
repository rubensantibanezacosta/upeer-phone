package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object SyncSettingsInbox {
  fun page(records: List<SyncEventRecord>, since: Long): JSONObject {
    val events = JSONArray()
    var cursor = since
    for (record in records) {
      if (record.sequence > cursor) cursor = record.sequence
      if (record.kind != SyncEventKinds.SETTINGS_UPDATE) continue
      if (record.direction != INBOUND) continue
      events.put(
        JSONObject()
          .put("sequence", record.sequence)
          .put("eventId", record.eventId)
          .put("occurredAt", record.occurredAt)
          .put("payload", record.payload),
      )
    }
    return JSONObject().put("cursor", cursor).put("events", events)
  }

  private const val INBOUND = "inbound"
}
