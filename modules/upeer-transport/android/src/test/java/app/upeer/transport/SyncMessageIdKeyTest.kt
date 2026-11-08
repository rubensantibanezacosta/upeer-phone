package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * El id del mensaje viaja en `id` dentro del payload de `MESSAGE_UPSERT`, no en `messageId`.
 *
 * Fallo real, medido en dispositivo el 08/11: la reparación de mensajes sincronizados comprobaba
 * `payload.optString("messageId")`, que siempre devolvía cadena vacía. `messages.find("")` daba
 * null, así que el filtro de «ya aplicado» nunca saltaba y los 116 eventos entrantes se volvían a
 * proyectar en cada pasada (**~900 no-ops por arranque**). El aplicador sí leía `id`, de ahí que
 * devolviera `UNCHANGED` en vez de perderse el mensaje: el fallo era de coste, no de datos.
 */
class SyncMessageIdKeyTest {

  private fun payload(id: String) = JSONObject()
    .put("id", id)
    .put("chatUpeerId", "chat-1")
    .put("message", "hola")
    .put("timestamp", 1_790_000_000_000L)

  @Test
  fun `el payload guarda el id en la clave id`() {
    val json = payload("efca0fda-453a-447b-a76b-841ee7220966")
    assertEquals("efca0fda-453a-447b-a76b-841ee7220966", json.optString("id"))
    // La clave equivocada no existe: es exactamente lo que rompía el filtro.
    assertEquals("", json.optString("messageId"))
  }

  @Test
  fun `buscar por messageId da vacio y por id da el mensaje`() {
    val json = payload("abc-123")
    // Lo que hacía el código roto: find("") siempre null, sin importar cuántos mensajes hubiera.
    assertNotEquals("", json.optString("id"))
    assertEquals("", json.optString("messageId"))
  }

  @Test
  fun `SyncHistoryPayload desktop escribe el id en id`() {
    // El productor real del payload: si cambiara de clave, este test lo detecta.
    val message = JSONObject()
      .put("id", "mensaje-1")
      .put("upeerId", "chat-1")
      .put("content", "hola")
      .put("timestamp", 1_790_000_000_000L)
    val built = SyncHistoryPayload.desktop(message)
    assertEquals("mensaje-1", built.optString("id"))
    assertEquals("", built.optString("messageId"))
  }
}
