package app.upeer.transport

import org.json.JSONObject

internal object SyncEventKinds {
  const val MESSAGE_UPSERT = "MESSAGE_UPSERT"
  const val MESSAGE_STATUS = "MESSAGE_STATUS"
  const val MESSAGE_EDIT = "MESSAGE_EDIT"
  const val MESSAGE_DELETE = "MESSAGE_DELETE"
  const val CHAT_CLEAR = "CHAT_CLEAR"
  const val REACTION_SET = "REACTION_SET"
  const val GROUP_UPSERT = "GROUP_UPSERT"
  const val GROUP_DELETE = "GROUP_DELETE"
  const val CONTACT_PROFILE = "CONTACT_PROFILE"

  private val all = setOf(
    MESSAGE_UPSERT,
    MESSAGE_STATUS,
    MESSAGE_EDIT,
    MESSAGE_DELETE,
    CHAT_CLEAR,
    REACTION_SET,
    GROUP_UPSERT,
    GROUP_DELETE,
    CONTACT_PROFILE,
  )

  private val groupKinds = setOf(GROUP_UPSERT, GROUP_DELETE)

  fun isValid(kind: String): Boolean = kind in all

  fun requiresGroupSupport(kind: String): Boolean = kind in groupKinds

  fun payloadObject(kind: String, payload: String): JSONObject? {
    if (!isValid(kind)) return null
    return try {
      JSONObject(payload)
    } catch (_: Exception) {
      null
    }
  }
}
