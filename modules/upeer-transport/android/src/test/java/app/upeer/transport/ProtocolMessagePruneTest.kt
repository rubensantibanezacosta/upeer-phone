package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolMessagePruneTest {
  private val chat = "aa".repeat(32)

  private fun state(vararg ids: Pair<String, String>): JSONObject {
    val messages = JSONArray()
    for ((peer, id) in ids) {
      messages.put(JSONObject().put("id", id).put("upeerId", peer))
    }
    return JSONObject().put("messages", messages)
  }

  @Test
  fun `vaciar un chat borra sus mensajes de disco`() {
    val known = ProtocolMessagePrune.index(state(chat to "m1", chat to "m2"))
    val gone = ProtocolMessagePrune.prune(known, ProtocolMessagePrune.index(state()))

    assertEquals(setOf("m1", "m2"), gone[chat])
  }

  @Test
  fun `borrar un mensaje suelto borra solo ese`() {
    val known = ProtocolMessagePrune.index(state(chat to "m1", chat to "m2", chat to "m3"))
    val gone = ProtocolMessagePrune.prune(known, ProtocolMessagePrune.index(state(chat to "m1", chat to "m3")))

    assertEquals(setOf("m2"), gone[chat])
  }

  @Test
  fun `un chat que cae fuera del tope de 500 no se borra`() {
    // El estado recorta a 500: el hueco son recortes, no bajas del usuario.
    val known = ProtocolMessagePrune.index(state(chat to "m1", chat to "m2", chat to "m3"))
    val full = (1..ProtocolMessagePrune.MAX_STATE_MESSAGES).map { chat to "n$it" }.toTypedArray()
    val gone = ProtocolMessagePrune.prune(known, ProtocolMessagePrune.index(state(*full)))

    assertTrue("Con el estado en el tope no se borra nada de esa conversación", gone[chat].isNullOrEmpty())
  }

  @Test
  fun `sin indice previo no se borra nada`() {
    assertTrue(ProtocolMessagePrune.prune(null, ProtocolMessagePrune.index(state())).isEmpty())
  }

  @Test
  fun `un mensaje nuevo no provoca bajas`() {
    val known = ProtocolMessagePrune.index(state(chat to "m1"))
    val gone = ProtocolMessagePrune.prune(known, ProtocolMessagePrune.index(state(chat to "m1", chat to "m2")))

    assertTrue(gone.isEmpty())
  }

  @Test
  fun `vaciar un chat no toca los mensajes de otro`() {
    val other = "bb".repeat(32)
    val known = ProtocolMessagePrune.index(state(chat to "m1", other to "o1"))
    val gone = ProtocolMessagePrune.prune(known, ProtocolMessagePrune.index(state(other to "o1")))

    assertEquals(setOf(chat), gone.keys)
    assertEquals(setOf("m1"), gone[chat])
  }
}
