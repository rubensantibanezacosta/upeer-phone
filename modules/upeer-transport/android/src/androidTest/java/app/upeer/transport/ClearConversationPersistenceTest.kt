package app.upeer.transport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba en el dispositivo que vaciar una conversación **se persiste en SQLite**.
 *
 * Es el caso que falló: `deleteConversation` quitaba los mensajes de la copia en memoria pero las
 * filas seguían en la tabla, así que al reiniciar volvían a aparecer. Como aquí no se puede pulsar en
 * pantalla, se ejerce la misma ruta nativa que usa la interfaz.
 */
@RunWith(AndroidJUnit4::class)
class ClearConversationPersistenceTest {
  // Nota: este módulo es una librería, así que su APK de test no se instala solo contra la app real
  // (com.upeer.upeer). Se ejecuta con `connectedDebugAndroidTest` cuando el proyecto lo engancha, o
  // se ejerce la misma ruta desde la app. La prueba JVM `ProtocolMessagePruneTest` cubre la lógica
  // de decisión sin dispositivo; esta cubre el efecto real sobre SQLite.
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun vaciarUnaConversacionBorraSusFilasDeSqlite() {
    val store = ProtocolStateStore(context)
    val messages = store.databaseHandle()
    val chat = "ab".repeat(32)
    val other = "cd".repeat(32)

    // Dos conversaciones de mentira, con dos mensajes cada una, guardadas por la misma ruta que usa
    // la app (`messages.save`), para que estado en memoria y SQLite queden coherentes desde el principio.
    for ((index, peer) in listOf(chat, other).withIndex()) {
      for (n in 0 until 2) {
        store.messages.save(
          JSONObject()
            .put("id", "prune-$index-$n")
            .put("upeerId", peer)
            .put("isMine", false)
            .put("content", "mensaje de prueba")
            .put("version", 1)
            .put("timestamp", System.currentTimeMillis() + n),
        )
      }
    }
    store.flushPersistence()
    val database = ProtocolMessageDatabase(messages)
    assertTrue("los mensajes de prueba deberían estar guardados", database.count() >= 4)

    // La ruta que usa la interfaz: cargar el estado, vaciar el chat y persistirlo.
    val loaded = org.json.JSONArray(store.messagesJSON(chat, 50))
    assertTrue("el chat de prueba debería tener mensajes", loaded.length() >= 2)
    val cleared = store.deleteConversation(chat)
    assertTrue("deleteConversation debería devolver true", cleared)
    assertTrue("el volcado a disco debería confirmarse", store.flushPersistence())

    // Y lo que importa: ya no están en la base, y el otro chat sigue entero.
    val after = database.conversation(chat, 50)
    assertEquals("la conversación vaciada no debería tener mensajes en SQLite", 0, after.length())
    assertTrue("el otro chat no debería tocarse", database.conversation(other, 50).length() >= 2)

    // Limpieza de los datos de prueba.
    database.deleteConversation(other)
    store.flushPersistence()
    assertFalse(database.conversation(other, 50).length() > 2)
  }
}
