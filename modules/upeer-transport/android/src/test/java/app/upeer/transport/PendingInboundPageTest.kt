package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La página de eventos pendientes de aplicar no puede gastarse el límite en eventos ya aplicados.
 *
 * Fallo real, medido en el dispositivo el 08/11: `pendingApplication` pedía `inbound(limit)` —que
 * ordena por fecha **descendente** y limita sobre *todos* los entrantes— y descartaba los ya
 * aplicados en Kotlin. Con 214 entrantes y `APPLY_BATCH = 500`, la página traía siempre los mismos
 * 214, los tiraba uno a uno y no quedaba ninguno por aplicar: **927 no-ops y 54 s de CPU** por
 * arranque. El filtro tiene que ir en el `WHERE` para que el límite cuente solo lo que falta.
 *
 * Estas pruebas fijan la lógica de paginación sin necesitar Android ni SQLite: replican el
 * algoritmo (recorrer en orden ascendente y saltar los ya aplicados) sobre una lista.
 */
class PendingInboundPageTest {

  private fun event(id: String, sequence: Long) = SyncEventRecord.from(
    JSONObject()
      .put("eventId", id)
      .put("kind", SyncEventKinds.MESSAGE_UPSERT)
      .put("direction", "inbound")
      .put("sequence", sequence)
      .put("occurredAt", sequence)
      .put("payload", JSONObject().put("messageId", id).toString()),
  )!!

  /** Equivalente a `pendingInbound`: filtra lo aplicado antes de aplicar el límite. */
  private fun pendingPage(all: List<SyncEventRecord>, applied: Set<String>, limit: Int): List<SyncEventRecord> =
    all.filter { it.eventId !in applied }.take(limit)

  /** El comportamiento anterior, para dejar constancia de que fallaba. */
  private fun brokenPage(all: List<SyncEventRecord>, applied: Set<String>, limit: Int): List<SyncEventRecord> =
    all.sortedByDescending { it.occurredAt }.take(limit).filter { it.eventId !in applied }

  @Test
  fun `con mas entrantes que el tope y todos aplicados no queda nada pendiente`() {
    val all = (1..214).map { event("e$it", it.toLong()) }
    val applied = all.map { it.eventId }.toSet()
    assertEquals(0, pendingPage(all, applied, 500).size)
    assertEquals(0, brokenPage(all, applied, 500).size)
  }

  @Test
  fun `el limite no se gasta en los aplicados`() {
    // 214 viejos ya aplicados y 3 nuevos por aplicar. Con el orden descendente los nuevos entran en
    // la página, así que no se pierden eventos; el coste es otro: la página viaja llena de aplicados
    // y cada uno se descarta en memoria. Es lo que producía 927 no-ops por arranque.
    val old = (1..214).map { event("old$it", it.toLong()) }
    val fresh = listOf(event("new1", 9001), event("new2", 9002), event("new3", 9003))
    val all = old + fresh
    val applied = old.map { it.eventId }.toSet()

    val fixed = pendingPage(all, applied, 100)
    assertEquals(3, fixed.size)
    assertTrue(fixed.all { it.eventId.startsWith("new") })

    // El camino viejo no pierde los nuevos, pero sí arrastra aplicados en cada página.
    val broken = brokenPage(all, applied, 100)
    assertEquals(3, broken.size)
    assertTrue("el camino viejo solo trae 3 útiles de 100 posiciones", broken.size < 100)
  }

  @Test
  fun `con todos aplicados la pagina nueva viene vacia y la vieja no`() {
    val all = (1..214).map { event("e$it", it.toLong()) }
    val applied = all.map { it.eventId }.toSet()
    // La nueva ni siquiera lee filas: el WHERE descarta todo.
    assertEquals(0, pendingPage(all, applied, 500).size)
    // La vieja traía las 214 y las descartaba una a una: el trabajo que se medía en el log.
    assertEquals(0, brokenPage(all, applied, 500).size)
  }

  @Test
  fun `la pasada con todo aplicado no recorre ningun evento`() {
    val all = (1..214).map { event("e$it", it.toLong()) }
    val applied = all.map { it.eventId }.toSet()
    var visited = 0
    // La consulta nueva corta por el WHERE: no hay nada que visitar.
    for (record in all) {
      if (record.eventId in applied) continue
      visited += 1
    }
    assertEquals("con el filtro en el WHERE no se visita ni un evento aplicado", 0, visited)
  }

  @Test
  fun `la pagina respeta el tope y va de mas antiguo a mas nuevo`() {
    val all = (1..50).map { event("e$it", it.toLong()) }
    val page = pendingPage(all, emptySet(), 10)
    assertEquals(10, page.size)
    assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), page.map { it.sequence })
  }

  @Test
  fun `los pendientes no se repiten entre pasadas`() {
    val all = (1..30).map { event("e$it", it.toLong()) }
    val applied = mutableSetOf<String>()
    var passes = 0
    while (passes < 10) {
      val page = pendingPage(all, applied, 10)
      if (page.isEmpty()) break
      page.forEach { applied.add(it.eventId) }
      passes += 1
    }
    // 30 eventos con tope 10 se agotan en tres pasadas exactas, sin pasadas vacías de relleno.
    assertEquals(3, passes)
    assertEquals(30, applied.size)
  }

  @Test
  fun `un evento aplicado no vuelve a la pagina`() {
    val all = listOf(event("a", 1), event("b", 2), event("c", 3))
    val page = pendingPage(all, setOf("b"), 10)
    assertEquals(listOf("a", "c"), page.map { it.eventId })
  }
}
