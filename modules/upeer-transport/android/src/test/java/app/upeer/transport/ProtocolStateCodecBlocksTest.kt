package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolStateCodecBlocksTest {
  private fun state(): JSONObject = JSONObject()
    .put("peers", JSONObject().put("a", JSONObject()))
    .put("sessions", JSONObject().put("b", JSONObject()))
    .put("messages", JSONArray().put(JSONObject().put("id", "m1")))
    .put("sync", JSONObject().put("events", JSONArray()))
    .put("reputation", JSONObject().put("scores", JSONObject()))

  @Test
  fun `cada parte grande del estado va a su propio bloque`() {
    val blocks = ProtocolStateCodec.split(state())

    assertTrue(blocks.containsKey("messages"))
    assertTrue(blocks.containsKey("sync"))
    assertTrue(blocks.containsKey("peers"))
    assertTrue(blocks.containsKey("sessions"))
    assertTrue(blocks.containsKey("reputation"))
  }

  @Test
  fun `el resto de claves cae en el bloque rest`() {
    val withExtra = state().put("announcedProfileV2", "x")

    val blocks = ProtocolStateCodec.split(withExtra)

    assertEquals("x", blocks.getValue("rest").optString("announcedProfileV2"))
    assertEquals(1, blocks.getValue("messages").length())
  }

  @Test
  fun `cambiar los mensajes no cambia el bloque de sincronizacion`() {
    val before = ProtocolStateCodec.split(state())
    val after = ProtocolStateCodec.split(state().put("messages", JSONArray().put(JSONObject().put("id", "m2"))))

    assertEquals(before.getValue("sync").toString(), after.getValue("sync").toString())
    assertNotEquals(before.getValue("messages").toString(), after.getValue("messages").toString())
  }

  @Test
  fun `repartir no pierde ninguna clave`() {
    val original = state()

    val blocks = ProtocolStateCodec.split(original)
    val rebuilt = JSONObject()
    for (value in blocks.values) {
      val keys = value.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        rebuilt.put(key, value.opt(key))
      }
    }

    val originalKeys = original.keys().asSequence().toSet()
    val rebuiltKeys = rebuilt.keys().asSequence().toSet()
    assertEquals(originalKeys, rebuiltKeys)
  }
}
