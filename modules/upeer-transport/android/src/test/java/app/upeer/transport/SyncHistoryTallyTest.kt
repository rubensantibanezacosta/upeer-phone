package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SyncHistoryTallyTest {
  @Test
  fun `una pagina de eventos ya conocidos cuenta como procesada`() {
    val entries = pageOf("evt-", 50)
    val known = entries.ids().toSet()

    val tally = tallyHistoryPage(entries, { false }, { it in known })

    assertEquals(50, tally.processed)
    assertEquals(0, tally.recorded)
    assertEquals(0, tally.failed)
  }

  @Test
  fun `una pagina de eventos nuevos cuenta registrada y procesada`() {
    val entries = pageOf("evt-", 50)

    val tally = tallyHistoryPage(entries, { true }, { false })

    assertEquals(50, tally.processed)
    assertEquals(50, tally.recorded)
    assertEquals(0, tally.failed)
  }

  @Test
  fun `un evento que no se registra ni estaba conocido queda rechazado`() {
    val entries = pageOf("evt-", 3)

    val tally = tallyHistoryPage(entries, { false }, { false })

    assertEquals(0, tally.processed)
    assertEquals(0, tally.recorded)
    assertEquals(3, tally.failed)
  }

  @Test
  fun `el progreso solo cuenta lo que de verdad queda por recorrer`() {
    val state = JSONObject()
    val history = SyncHistoryState({ state }, Any(), {})

    history.savePage(DEVICE, "10:a", false, 50, 48)
    assertEquals(50, history.progress(DEVICE).applied)
    assertEquals(98, history.progress(DEVICE).total)

    history.savePage(DEVICE, null, true, 48, 0)
    val progress = history.progress(DEVICE)
    assertEquals(98, progress.applied)
    assertEquals(98, progress.total)
    assertTrue(progress.done)
  }

  @Test
  fun `un historico ya terminado no anuncia trabajo pendiente`() {
    val state = JSONObject()
    val history = SyncHistoryState({ state }, Any(), {})
    state.put("history", JSONObject().put(DEVICE, JSONObject().put("done", true).put("applied", 2).put("total", 50).put("known", true)))

    val progress = history.progress(DEVICE)

    assertEquals(50, progress.applied)
    assertEquals(50, progress.total)
    assertTrue(progress.done)
  }

  @Test
  fun `dos paginas solapadas no dejan el contador por encima del total`() {
    val state = JSONObject()
    val history = SyncHistoryState({ state }, Any(), {})
    val start = CountDownLatch(1)
    val threads = (1..4).map { worker ->
      Thread {
        start.await()
        repeat(50) { round ->
          history.savePage(DEVICE, "$worker-$round:x", false, 25, 0)
        }
      }
    }
    threads.forEach { it.start() }
    start.countDown()
    threads.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }

    val progress = history.progress(DEVICE)
    assertEquals(5_000, progress.applied)
    assertEquals(5_000, progress.total)
  }

  @Test
  fun `el progreso solo cuenta mensajes y no los eventos de acompanamiento`() {
    val entries = JSONArray()
      .put(JSONObject().put("eventId", "history-a").put("kind", SyncEventKinds.MESSAGE_UPSERT))
      .put(JSONObject().put("eventId", "history-b").put("kind", SyncEventKinds.MESSAGE_UPSERT))
      .put(JSONObject().put("eventId", "profile-a").put("kind", SyncEventKinds.CONTACT_PROFILE))
      .put(JSONObject().put("eventId", "group-a").put("kind", SyncEventKinds.GROUP_UPSERT))

    val tally = tallyHistoryPage(entries, { true }, { false })

    assertEquals(4, tally.recorded)
    assertEquals(2, tally.messages)
  }

  @Test
  fun `los mensajes ya conocidos tambien cuentan como recorridos`() {
    val entries = JSONArray()
      .put(JSONObject().put("eventId", "history-a").put("kind", SyncEventKinds.MESSAGE_UPSERT))
      .put(JSONObject().put("eventId", "history-b").put("kind", SyncEventKinds.MESSAGE_UPSERT))
    val known = entries.ids().toSet()

    val tally = tallyHistoryPage(entries, { false }, { it in known })

    assertEquals(0, tally.recorded)
    assertEquals(2, tally.messages)
  }

  @Test
  fun `repetir una pagina ya recorrida no infla el avance`() {
    val state = JSONObject()
    val history = SyncHistoryState({ state }, Any(), {})

    history.savePage(DEVICE, "10:a", false, 3, 5)
    history.savePage(DEVICE, "10:a", false, 3, 5)

    val progress = history.progress(DEVICE)
    assertEquals(3, progress.applied)
    assertEquals(8, progress.total)
  }

  private fun pageOf(prefix: String, count: Int): JSONArray {
    val entries = JSONArray()
    for (index in 0 until count) entries.put(JSONObject().put("eventId", "$prefix$index"))
    return entries
  }

  private fun JSONArray.ids(): List<String> =
    (0 until length()).map { optJSONObject(it).optString("eventId") }

  private companion object {
    const val DEVICE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
