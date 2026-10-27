package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProgressTest {
  private fun entry(applied: Int = 0, total: Int = 0, known: Boolean = false) =
    SyncHistoryProgress(cursor = null, done = false, applied = applied, total = total, known = known)

  @Test
  fun `sin pendiente anunciado la barra queda indeterminada`() {
    val progress = SyncProgress.json(listOf(entry(applied = 50)), pendingEvents = 0, activeFiles = 0, phase = SyncStatusReport.STATE_SYNCING)

    assertFalse(progress.optBoolean("determinate"))
    assertEquals(0, progress.optInt("percent"))
    assertEquals("50 mensajes sincronizados", progress.optString("detail"))
  }

  @Test
  fun `con total anunciado el porcentaje sale de los mensajes aplicados`() {
    val progress = SyncProgress.json(listOf(entry(applied = 250, total = 1_000, known = true)), 0, 0, SyncStatusReport.STATE_SYNCING)

    assertTrue(progress.optBoolean("determinate"))
    assertEquals(25, progress.optInt("percent"))
    assertEquals("250 de 1000 mensajes", progress.optString("detail"))
    assertEquals("Sincronizando tus dispositivos…", progress.optString("headline"))
  }

  @Test
  fun `sin mensajes aplicados ni total conocido se anuncia la conexion`() {
    val progress = SyncProgress.json(emptyList(), pendingEvents = 0, activeFiles = 0, phase = SyncStatusReport.STATE_SYNCING)

    assertEquals("Conectando con tus dispositivos", progress.optString("detail"))
    assertFalse(progress.optBoolean("determinate"))
  }

  @Test
  fun `suma los eventos pendientes y los archivos en curso al detalle`() {
    val progress = SyncProgress.json(listOf(entry(applied = 5, total = 10, known = true)), 3, 2, SyncStatusReport.STATE_SYNCING)

    assertEquals("5 de 10 mensajes · 3 eventos pendientes de aplicar · 2 archivos en curso", progress.optString("detail"))
  }

  @Test
  fun `usa el singular con una sola unidad pendiente`() {
    val progress = SyncProgress.json(emptyList(), pendingEvents = 1, activeFiles = 1, phase = SyncStatusReport.STATE_SYNCING)

    assertEquals("Conectando con tus dispositivos · 1 evento pendiente de aplicar · 1 archivo en curso", progress.optString("detail"))
  }

  @Test
  fun `una parte sin total conocido deja la barra indeterminada`() {
    val progress = SyncProgress.json(
      listOf(entry(applied = 10, total = 40, known = true), entry(applied = 5)),
      pendingEvents = 0,
      activeFiles = 0,
      phase = SyncStatusReport.STATE_SYNCING,
    )

    assertFalse(progress.optBoolean("determinate"))
  }

  @Test
  fun `al dia cambia el titular`() {
    val progress = SyncProgress.json(listOf(entry(applied = 10, total = 10, known = true)), 0, 0, SyncStatusReport.STATE_LIVE)

    assertEquals("¡Al día!", progress.optString("headline"))
    assertEquals(100, progress.optInt("percent"))
  }

  @Test
  fun `los contadores del historico se acumulan y se cierran`() {
    val state = JSONObject()
    val history = SyncHistoryState({ state }, Any(), {})

    history.addCounters(DEVICE, 50, 150)
    assertEquals(50, history.progress(DEVICE).applied)
    assertEquals(200, history.progress(DEVICE).total)

    history.addCounters(DEVICE, 50, 100)
    assertEquals(100, history.progress(DEVICE).applied)
    assertEquals(200, history.progress(DEVICE).total)
    assertTrue(history.progress(DEVICE).known)

    history.finishCounters(DEVICE)
    assertEquals(200, history.progress(DEVICE).applied)
    assertEquals(200, history.progress(DEVICE).total)
  }

  @Test
  fun `un pendiente desconocido no marca el total como conocido`() {
    val state = JSONObject()
    val history = SyncHistoryState({ state }, Any(), {})

    history.addCounters(DEVICE, 30, null)

    assertEquals(30, history.progress(DEVICE).applied)
    assertFalse(history.progress(DEVICE).known)
  }

  @Test
  fun `guardar una pagina actualiza cursor y contadores en una sola escritura`() {
    val state = JSONObject()
    var writes = 0
    val history = SyncHistoryState({ state }, Any(), { writes += 1 })

    history.savePage(DEVICE, "17:id", false, 50, 150)
    history.savePage(DEVICE, "34:id", false, 50, 100)

    assertEquals(2, writes)
    val progress = history.progress(DEVICE)
    assertEquals("34:id", progress.cursor)
    assertEquals(100, progress.applied)
    assertEquals(200, progress.total)
    assertTrue(progress.known)
  }

  @Test
  fun `la ultima pagina cierra los contadores`() {
    val state = JSONObject()
    val history = SyncHistoryState({ state }, Any(), {})

    history.savePage(DEVICE, "17:id", false, 50, 150)
    history.savePage(DEVICE, null, true, 150, 0)

    val progress = history.progress(DEVICE)
    assertTrue(progress.done)
    assertEquals(200, progress.applied)
    assertEquals(200, progress.total)
  }

  private companion object {
    val DEVICE = "aa".repeat(32)
  }
}
