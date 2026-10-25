package app.upeer.transport.vault

import android.content.Context
import app.upeer.transport.KeystoreStore
import org.json.JSONArray
import org.json.JSONObject

internal class VaultMailboxStore(context: Context) {
  private val store = KeystoreStore(
    context = context,
    preferencesName = "upeer_vault_mailbox",
    keyAlias = "upeer-vault-mailbox",
    valueKey = "vault_state",
  )
  private val lock = Any()
  private var state = loadState()

  fun snapshot(nowMillis: Long): VaultMailboxState = synchronized(lock) {
    val purged = VaultMailbox.purge(state, nowMillis)
    if (purged.entries.size != state.entries.size) {
      state = purged
      persist()
    }
    state
  }

  fun insert(entry: VaultEntry, nowMillis: Long): VaultMailboxInsert = synchronized(lock) {
    val result = VaultMailbox.insert(state, entry, nowMillis)
    if (result is VaultMailboxInsert.Stored) {
      state = result.state
      persist()
    }
    result
  }

  fun release(payloadHashes: Collection<String>): Int = synchronized(lock) {
    val (next, removed) = VaultMailbox.release(state, payloadHashes)
    if (removed > 0) {
      state = next
      persist()
    }
    removed
  }

  fun entry(payloadHash: String, nowMillis: Long): VaultEntry? = synchronized(lock) {
    val hash = payloadHash.lowercase()
    snapshot(nowMillis).entries.firstOrNull { it.payloadHash == hash }
  }

  fun renew(payloadHash: String, expiresAt: Long): Boolean = synchronized(lock) {
    val (next, renewed) = VaultMailbox.renew(state, payloadHash, expiresAt)
    if (renewed) {
      state = next
      persist()
    }
    renewed
  }

  fun expiringSoon(windowMillis: Long, nowMillis: Long): List<VaultEntry> = synchronized(lock) {
    VaultMailbox.expiringSoon(snapshot(nowMillis), windowMillis, nowMillis)
  }

  fun clear() = synchronized(lock) {
    state = VaultMailboxState(1L, emptyList())
    store.clear()
  }

  private fun persist() {
    val entries = JSONArray()
    for (entry in state.entries) {
      entries.put(
        JSONObject()
          .put("id", entry.id)
          .put("hash", entry.payloadHash)
          .put("recipient", entry.recipientSid)
          .put("sender", entry.senderSid)
          .put("priority", entry.priority)
          .put("data", entry.data)
          .put("expiresAt", entry.expiresAt)
          .apply { if (entry.recipientDeviceId != null) put("device", entry.recipientDeviceId) },
      )
    }
    val document = JSONObject().put("nextId", state.nextId).put("entries", entries)
    store.save(document.toString().toByteArray(Charsets.UTF_8))
  }

  private fun loadState(): VaultMailboxState {
    val raw = store.load() ?: return VaultMailboxState(1L, emptyList())
    val document = runCatching { JSONObject(String(raw, Charsets.UTF_8)) }.getOrNull()
      ?: return VaultMailboxState(1L, emptyList())
    val entries = mutableListOf<VaultEntry>()
    val array = document.optJSONArray("entries") ?: JSONArray()
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val hash = item.optString("hash")
      val sender = item.optString("sender")
      if (hash.isEmpty() || sender.isEmpty()) continue
      entries.add(
        VaultEntry(
          id = item.optLong("id", 0L),
          payloadHash = hash,
          recipientSid = item.optString("recipient"),
          recipientDeviceId = item.optString("device").takeIf(String::isNotEmpty),
          senderSid = sender,
          priority = item.optInt("priority", VaultStoreEnvelope.PRIORITY_MESSAGE),
          data = item.optString("data"),
          expiresAt = item.optLong("expiresAt", 0L),
        ),
      )
    }
    val maxId = entries.maxOfOrNull { it.id } ?: 0L
    val nextId = maxOf(document.optLong("nextId", 1L), maxId + 1)
    return VaultMailboxState(nextId, entries)
  }
}
