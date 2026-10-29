package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSettingsInboxTest {
  @Test
  fun onlyInboundSettingsEventsAreReturned() {
    val page = SyncSettingsInbox.page(
      listOf(
        record(1, SyncEventKinds.MESSAGE_UPSERT, "inbound"),
        record(2, SyncEventKinds.SETTINGS_UPDATE, "outbound"),
        record(3, SyncEventKinds.SETTINGS_UPDATE, "inbound"),
      ),
      0,
    )

    val events = page.getJSONArray("events")
    assertEquals(1, events.length())
    assertEquals(3L, events.getJSONObject(0).getLong("sequence"))
  }

  @Test
  fun theCursorAdvancesPastEventsThatAreNotOurs() {
    val page = SyncSettingsInbox.page(
      listOf(
        record(4, SyncEventKinds.MESSAGE_UPSERT, "inbound"),
        record(5, SyncEventKinds.MESSAGE_STATUS, "inbound"),
      ),
      0,
    )

    assertEquals(5L, page.getLong("cursor"))
    assertEquals(0, page.getJSONArray("events").length())
  }

  @Test
  fun anEmptyPageKeepsTheCursor() {
    val page = SyncSettingsInbox.page(emptyList(), 42)

    assertEquals(42L, page.getLong("cursor"))
    assertEquals(0, page.getJSONArray("events").length())
  }

  @Test
  fun thePayloadTravelsWhole() {
    val page = SyncSettingsInbox.page(
      listOf(record(7, SyncEventKinds.SETTINGS_UPDATE, "inbound", JSONObject().put("block", "chat").put("muted", true))),
      6,
    )

    val event = page.getJSONArray("events").getJSONObject(0)
    assertEquals("chat", event.getJSONObject("payload").optString("block"))
    assertTrue(event.getJSONObject("payload").optBoolean("muted"))
    assertEquals(7L, event.getLong("occurredAt"))
  }

  private fun record(
    sequence: Long,
    kind: String,
    direction: String,
    payload: JSONObject = JSONObject().put("block", "chat"),
  ): SyncEventRecord = SyncEventRecord(
    eventId = "event-$sequence",
    kind = kind,
    occurredAt = sequence,
    sequence = sequence,
    payload = payload,
    direction = direction,
    originDeviceId = null,
    originSignature = null,
    originCertificate = null,
  )
}
