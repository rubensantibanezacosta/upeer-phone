package app.upeer.transport

internal class NotificationInbox(private val maxLines: Int = MAX_LINES) {
  private data class Entry(val label: String, val preview: String)

  private val lock = Any()
  private val entries = LinkedHashMap<String, Entry>()
  private var messages = 0

  fun record(conversationId: String, label: String, preview: String) {
    val id = conversationId.trim()
    if (id.isEmpty()) return
    synchronized(lock) {
      entries.remove(id)
      entries[id] = Entry(label.trim(), preview.trim())
      while (entries.size > maxLines) {
        val oldest = entries.keys.firstOrNull() ?: break
        entries.remove(oldest)
      }
      messages += 1
    }
  }

  fun forget(conversationId: String) {
    synchronized(lock) { entries.remove(conversationId.trim()) }
  }

  fun clear() = synchronized(lock) {
    entries.clear()
    messages = 0
  }

  fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }

  fun conversations(): Int = synchronized(lock) { entries.size }

  fun messages(): Int = synchronized(lock) { messages }

  fun lines(): List<String> = synchronized(lock) {
    entries.values.reversed().map { entry ->
      if (entry.preview.isEmpty()) entry.label else "${entry.label}: ${entry.preview}"
    }
  }

  fun summaryText(): String = synchronized(lock) {
    if (entries.isEmpty()) return ""
    "${count(messages, "mensaje nuevo", "mensajes nuevos")} en ${count(entries.size, "conversación", "conversaciones")}"
  }

  private fun count(value: Int, singular: String, plural: String): String =
    if (value == 1) "1 $singular" else "$value $plural"

  internal companion object {
    const val MAX_LINES = 5
  }
}
