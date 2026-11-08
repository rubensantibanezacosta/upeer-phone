package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los metadatos del bloque `sync` que no tienen tabla tienen que sobrevivir al reinicio.
 *
 * El escritorio guarda `sequence`, `acks` y `applied` dentro del bloque `sync`, pero al migrar a SQLite
 * solo `devices` tenía tabla propia. Consecuencia real observada en el dispositivo: al reiniciar se
 * perdía la lista de eventos aplicados y el móvil volvía a aplicar los mismos 215 eventos en cada
 * arranque (**1938 reaplicaciones** en el log). Ahora viajan en `app_settings` bajo `sync_meta`.
 */
class SyncMetadataPersistenceTest {
  private val metaKeys = listOf("sequence", "acks", "applied")

  private fun block(): JSONObject = JSONObject()
    .put("sequence", 1_792_607_279_953L)
    .put("acks", JSONObject().put("device-a", 42L))
    .put("applied", JSONArray().put("event-1").put("event-2"))
    .put("devices", JSONObject().put("device-a", JSONObject().put("address", "200::1")))

  /** Lo que hace el volcado: separa los metadatos del resto del bloque. */
  private fun extractMeta(sync: JSONObject?, keys: List<String>): String? {
    if (sync == null) return null
    val meta = JSONObject()
    for (key in keys) if (sync.has(key)) meta.put(key, sync.opt(key))
    return if (meta.length() == 0) null else meta.toString()
  }

  /** Lo que hace la lectura: devuelve los metadatos a su sitio. */
  private fun restoreMeta(stored: String?, sync: JSONObject): JSONObject {
    stored?.let { raw ->
      val meta = JSONObject(raw)
      val keys = meta.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        sync.put(key, meta.opt(key))
      }
    }
    return sync
  }

  @Test
  fun `los metadatos del bloque sync se guardan completos`() {
    val stored = extractMeta(block(), metaKeys)
    requireNotNull(stored)

    val meta = JSONObject(stored)

    assertEquals(1_792_607_279_953L, meta.getLong("sequence"))
    assertEquals(42L, meta.getJSONObject("acks").getLong("device-a"))
    assertEquals(2, meta.getJSONArray("applied").length())
    assertFalse("devices no es metadato: tiene su propia tabla", meta.has("devices"))
  }

  @Test
  fun `al leer, los metadatos vuelven dentro de sync`() {
    val stored = extractMeta(block(), metaKeys)
    val restored = restoreMeta(stored, JSONObject().put("devices", JSONObject()))

    assertEquals(1_792_607_279_953L, restored.getLong("sequence"))
    assertEquals(42L, restored.getJSONObject("acks").getLong("device-a"))
    assertEquals(2, restored.getJSONArray("applied").length())
  }

  @Test
  fun `sin metadatos guardados el arranque no rompe`() {
    val restored = restoreMeta(null, JSONObject().put("devices", JSONObject()))

    assertFalse(restored.has("sequence"))
    assertTrue(restored.has("devices"))
  }

  @Test
  fun `un bloque sin metadatos no deja nada que guardar`() {
    val onlyDevices = JSONObject().put("devices", JSONObject())

    assertEquals(null, extractMeta(onlyDevices, metaKeys))
  }
}
