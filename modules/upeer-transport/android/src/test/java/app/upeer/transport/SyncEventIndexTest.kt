package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre los caminos del registro de eventos que se rompieron al migrar a SQLite.
 *
 * El fallo en el dispositivo era `Frame de control rechazado: No value for events`: con la base de
 * datos, `bucket()` no lleva `events` (la lista vive en la tabla), pero varias lecturas seguían pidiendo
 * el array del bloque. La sincronización se quedaba muda sin decir por qué. Estas pruebas fijan la
 * lógica que tienen que compartir los dos almacenes.
 */
class SyncEventIndexTest {
  private fun event(
    id: String,
    direction: String = "inbound",
    sequence: Long = 1L,
    kind: String = SyncEventKinds.MESSAGE_UPSERT,
  ): JSONObject = JSONObject()
    .put("eventId", id)
    .put("kind", kind)
    .put("occurredAt", 1_700_000_000_000L)
    .put("sequence", sequence)
    .put("direction", direction)
    .put("payload", JSONObject().put("id", "message-1").toString())

  private fun events(vararg items: JSONObject) = JSONArray().also { array ->
    for (item in items) array.put(item)
  }

  @Test
  fun `encuentra un evento por id`() {
    val array = events(event("a"), event("b"), event("c"))

    assertEquals(1, SyncEventIndex.indexOf(array, "b"))
    assertEquals(-1, SyncEventIndex.indexOf(array, "z"))
  }

  @Test
  fun `los eventos aplicados no vuelven a la cola`() {
    val array = events(event("a", sequence = 1), event("b", sequence = 2))
    val applied = JSONArray().put("a")

    val pending = SyncEventIndex.pendingApplication(array, applied, 10)

    assertEquals(listOf("b"), pending.map { it.eventId })
  }

  @Test
  fun `los outbound no se aplican`() {
    val array = events(event("a", direction = "outbound"), event("b", direction = "inbound"))

    val pending = SyncEventIndex.pendingApplication(array, null, 10)

    assertEquals(listOf("b"), pending.map { it.eventId })
  }

  @Test
  fun `se aplican por orden de secuencia`() {
    val array = events(event("c", sequence = 30), event("a", sequence = 10), event("b", sequence = 20))

    val pending = SyncEventIndex.pendingApplication(array, null, 10)

    assertEquals(listOf("a", "b", "c"), pending.map { it.eventId })
  }

  @Test
  fun `sin lista de aplicados cuenta todos los inbound`() {
    val array = events(event("a"), event("b", direction = "outbound"), event("c"))

    assertEquals(2, SyncEventIndex.pendingCount(array, null))
  }

  @Test
  fun `con lista de aplicados descuenta los ya aplicados`() {
    val array = events(event("a"), event("b"))
    val applied = JSONArray().put("a")

    assertEquals(1, SyncEventIndex.pendingCount(array, applied))
  }

  @Test
  fun `un array vacio no rompe nada`() {
    val empty = JSONArray()

    assertTrue(SyncEventIndex.pendingApplication(empty, null, 10).isEmpty())
    assertEquals(0, SyncEventIndex.pendingCount(empty, null))
    assertEquals(-1, SyncEventIndex.indexOf(empty, "a"))
    assertTrue(SyncEventIndex.byId(empty).isEmpty())
  }

  @Test
  fun `indexa por id para consultas del bloque en memoria`() {
    val map = SyncEventIndex.byId(events(event("a"), event("b")))

    assertEquals(setOf("a", "b"), map.keys)
    assertEquals("a", map.getValue("a").getString("eventId"))
  }
}
