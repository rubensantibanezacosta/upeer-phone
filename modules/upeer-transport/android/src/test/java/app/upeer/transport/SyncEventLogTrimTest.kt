package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEventLogTrimTest {
  private val lock = Any()
  private var state = JSONObject()
  private var persists = 0

  private fun log(maxEvents: Int) = SyncEventLog({ state }, lock, { persists += 1 }, maxEvents)

  private fun draft(index: Int) = SyncEventDraft(
    eventId = "%064d".format(index),
    kind = SyncEventKinds.MESSAGE_UPSERT,
    occurredAt = 1_793_000_000_000L + index,
    payload = JSONObject().put("id", "m$index"),
    direction = "outbound",
  )

  @Test
  fun `el registro no crece sin limite`() {
    val events = log(maxEvents = 5)
    for (index in 1..40) events.record(draft(index), 1_793_000_000_000L + index)

    assertEquals(5, events.eventsAfter(0L, 100).size)
  }

  @Test
  fun `al recortar sobreviven los eventos mas recientes`() {
    val events = log(maxEvents = 3)
    for (index in 1..9) events.record(draft(index), 1_793_000_000_000L + index)

    val kept = events.eventsAfter(0L, 100).map { it.payload.optString("id") }

    assertEquals(listOf("m7", "m8", "m9"), kept)
  }

  @Test
  fun `un evento repetido no se registra dos veces`() {
    val events = log(maxEvents = 10)
    events.record(draft(1), 1_793_000_000_001L)

    assertEquals(null, events.record(draft(1), 1_793_000_000_002L))
    assertEquals(1, events.eventsAfter(0L, 100).size)
    assertTrue(events.contains(draft(1).eventId))
  }

  @Test
  fun `registrar un evento persiste una sola vez`() {
    val events = log(maxEvents = 10)

    events.record(draft(1), 1_793_000_000_001L)

    assertEquals(1, persists)
  }
}
