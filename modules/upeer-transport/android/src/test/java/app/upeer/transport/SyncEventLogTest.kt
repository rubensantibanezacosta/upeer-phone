package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEventLogTest {
  private val now = 1_700_000_000_000L

  private fun log(maxEvents: Int = 2000): Triple<JSONObject, SyncEventLog, () -> Int> {
    val state = JSONObject()
    var persisted = 0
    val log = SyncEventLog({ state }, Any(), { persisted += 1 }, maxEvents)
    return Triple(state, log, { persisted })
  }

  private fun draft(eventId: String = "event-0001", kind: String = SyncEventKinds.MESSAGE_UPSERT) = SyncEventDraft(
    eventId = eventId,
    kind = kind,
    occurredAt = now,
    payload = JSONObject().put("id", "message-1"),
    direction = "outbound",
  )

  @Test
  fun `records an event with a monotonic sequence`() {
    val (state, log, persisted) = log()

    val record = log.record(draft(), now)

    assertNotNull(record)
    assertEquals(now * 1000, record!!.sequence)
    assertEquals(1, persisted())
    assertEquals(record.sequence, log.maxSequence())
    assertEquals(1, state.getJSONObject("sync").getJSONArray("events").length())
  }

  @Test
  fun `rejects a duplicate event id`() {
    val (_, log, _) = log()
    log.record(draft(), now)

    assertNull(log.record(draft(), now))
    assertTrue(log.contains("event-0001"))
  }

  @Test
  fun `rejects an unknown kind`() {
    val (_, log, _) = log()

    assertNull(log.record(draft(kind = "SYNC_PULSE"), now))
    assertFalse(log.contains("event-0001"))
  }

  @Test
  fun `rejects an event without an occurrence time`() {
    val (_, log, _) = log()

    assertNull(log.record(draft().copy(occurredAt = 0L), now))
  }

  @Test
  fun `never reuses a sequence even with a rewound clock`() {
    val (_, log, _) = log()

    val first = log.record(draft("event-0001"), now)!!
    val second = log.record(draft("event-0002"), now - 60_000)!!

    assertEquals(first.sequence + 1, second.sequence)
  }

  @Test
  fun `lists only the events after a sequence`() {
    val (_, log, _) = log()
    val first = log.record(draft("event-0001"), now)!!
    val second = log.record(draft("event-0002"), now)!!

    val events = log.eventsAfter(first.sequence, 10)

    assertEquals(listOf(second.eventId), events.map { it.eventId })
  }

  @Test
  fun `tracks the acknowledged sequence per device`() {
    val (_, log, _) = log()
    val record = log.record(draft(), now)!!
    val deviceId = "c".repeat(64)

    assertTrue(log.recordAck(deviceId, record.sequence))
    assertEquals(record.sequence, log.ackFor(deviceId))

    assertFalse(log.recordAck(deviceId, record.sequence - 1))
    assertEquals(record.sequence, log.ackFor(deviceId))
  }

  @Test
  fun `counts the events still pending for a device`() {
    val (_, log, _) = log()
    val deviceId = "c".repeat(64)
    val first = log.record(draft("event-0001"), now)!!
    log.record(draft("event-0002"), now)

    assertEquals(2, log.pendingFor(deviceId))
    log.recordAck(deviceId, first.sequence)
    assertEquals(1, log.pendingFor(deviceId))
  }

  @Test
  fun `drops the oldest events once the log is full`() {
    val (_, log, _) = log(maxEvents = 3)

    for (index in 1..5) {
      log.record(draft("event-000$index"), now + index)
    }

    assertFalse(log.contains("event-0001"))
    assertFalse(log.contains("event-0002"))
    assertTrue(log.contains("event-0005"))
  }
}
