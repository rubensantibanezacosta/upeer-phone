package app.upeer.transport

import org.json.JSONObject

/**
 * Decide qué mensajes hay que borrar de disco al volcar el estado.
 *
 * Vive aparte de [ProtocolStateMirror] porque la decisión es la parte delicada y se prueba en JVM:
 * `ProtocolMessageState.upsert` recorta el estado a [MAX_STATE_MESSAGES], así que «falta en el estado»
 * no siempre significa «lo ha borrado el usuario». Solo se borra un id si la conversación conocía ese
 * id y ahora trae menos mensajes que el tope, que es lo que ocurre al vaciar un chat o borrar uno.
 */
internal object ProtocolMessagePrune {
  const val MAX_STATE_MESSAGES = 500

  /** Ids por conversación presentes en [state]. */
  fun index(state: JSONObject): Map<String, Set<String>> {
    val list = state.optJSONArray("messages") ?: return emptyMap()
    val byChat = HashMap<String, MutableSet<String>>()
    for (index in 0 until list.length()) {
      val message = list.optJSONObject(index) ?: continue
      val chat = message.optString("upeerId")
      val id = message.optString("id")
      if (chat.isEmpty() || id.isEmpty()) continue
      byChat.getOrPut(chat) { HashSet() }.add(id)
    }
    return byChat
  }

  /** Ids por conversación que hay que borrar de la base, dados el índice anterior y el nuevo. */
  fun prune(known: Map<String, Set<String>>?, present: Map<String, Set<String>>): Map<String, Set<String>> {
    if (known == null) return emptyMap()
    val result = HashMap<String, Set<String>>()
    for ((chat, ids) in known) {
      val current = present[chat] ?: emptySet()
      // Con la conversación en el tope, el hueco puede ser un recorte, no una baja: no se borra.
      if (current.size >= MAX_STATE_MESSAGES) continue
      val gone = ids - current
      if (gone.isNotEmpty()) result[chat] = gone
    }
    return result
  }

  /** Igual que [prune] pero partiendo de los `JSONArray` sin indexar. */
  fun goneIds(known: Map<String, Set<String>>?, state: JSONObject): Map<String, Set<String>> =
    prune(known, index(state))
}
