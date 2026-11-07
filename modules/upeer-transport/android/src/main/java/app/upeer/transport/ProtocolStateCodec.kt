package app.upeer.transport

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal object ProtocolStateCodec {
  val BLOCKS = listOf("messages", "sync", "peers", "sessions", "groups", "reputation", "rest")

  fun blockOf(key: String): String = when (key) {
    "messages" -> "messages"
    "sync" -> "sync"
    "peers" -> "peers"
    "sessions" -> "sessions"
    "groups" -> "groups"
    "reputation" -> "reputation"
    else -> "rest"
  }

  fun keysOf(block: String): List<String> = when (block) {
    "rest" -> REST_KEYS
    else -> listOf(block)
  }

  val REST_KEYS = listOf(
    "profile",
    "announcedProfileV2",
    "retention",
    "preferences",
    "presence",
    "typing",
    "blocked",
    "notificationPrefs",
    "readReceipts",
    "shareActivity",
    "knownContacts",
    "settingsClock",
    "muted",
    "starred",
    "chatLists",
  )

  fun split(state: JSONObject): Map<String, JSONObject> {
    val blocks = HashMap<String, JSONObject>()
    val rest = JSONObject()
    val keys = state.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      val block = blockOf(key)
      if (block == "rest") {
        rest.put(key, state.opt(key))
      } else {
        blocks.getOrPut(block) { JSONObject() }.put(key, state.opt(key))
      }
    }
    blocks["rest"] = rest
    return blocks
  }

  fun load(store: KeystoreStore): JSONObject {
    val merged = empty()
    for (block in BLOCKS) {
      val raw = store.loadFrom(KeystoreStore.BLOCK_PREFIX + block) ?: continue
      val parsed = runCatching { JSONObject(String(raw, Charsets.UTF_8)) }.getOrElse { cause ->
        Log.e(TAG, "El bloque $block del estado no es JSON válido", cause)
        JSONObject()
      }
      val keys = parsed.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        merged.put(key, parsed.opt(key))
      }
    }
    return normalize(merged)
  }

  fun normalize(decoded: JSONObject): JSONObject {
    if (decoded.optJSONObject("peers") == null) decoded.put("peers", JSONObject())
    if (decoded.optJSONObject("sessions") == null) decoded.put("sessions", JSONObject())
    if (decoded.optJSONArray("messages") == null) decoded.put("messages", JSONArray())
    if (decoded.optJSONObject("sync") == null) decoded.put("sync", JSONObject())
    return decoded
  }

  fun empty(): JSONObject = JSONObject()
    .put("peers", JSONObject())
    .put("sessions", JSONObject())
    .put("messages", JSONArray())
    .put("sync", JSONObject())

  private fun eventFields(state: JSONObject): String {
    val events = state.optJSONObject("sync")?.optJSONArray("events") ?: return ""
    val sample = events.optJSONObject(0) ?: return ""
    val parts = ArrayList<String>()
    val keys = sample.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      parts.add("$key=${sample.opt(key)?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0}")
    }
    return "evento0[$parts]"
  }

  private fun sizes(state: JSONObject): String {    val parts = ArrayList<String>()
    val keys = state.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      parts.add("$key=${state.opt(key)?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0}")
    }
    parts.sortByDescending { it.substringAfter('=').toIntOrNull() ?: 0 }
    return parts.joinToString(" ")
  }

  private const val TAG = "UPeerTransport"
}
