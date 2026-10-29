package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class OutboxGiveUp(
  private val outbox: OutboxStore,
  private val state: ProtocolStateStore,
  private val sealer: OutboxSealer,
  private val vault: OutboxVault,
  private val deviceBindings: DeviceMessagingBindings,
) {
  fun retry(activeBridge: Bridge, entryID: String, entry: JSONObject, reason: String) {
    val attempts = entry.optInt("attempts", 0) + 1
    if (!OutboxRetryPolicy.canRetry(attempts)) {
      fail(activeBridge, entryID, entry, "descartada tras $attempts intentos: $reason")
      return
    }
    val backoff = OutboxRetryPolicy.backoffMs(attempts)
    outbox.markAttempt(entryID, attempts, System.currentTimeMillis() + backoff, reason)
    Log.w(TAG, "Reintento $attempts de $entryID para ${entry.optString("upeerId")} en ${backoff}ms: $reason")
  }

  fun fail(activeBridge: Bridge, entryID: String, entry: JSONObject, reason: String) {
    if (OutboxFallback.deservesVault(entry) && rescue(activeBridge, entry, reason)) return
    outbox.remove(entryID)
    if (entry.optString("kind", OutboxFallback.KIND_CHAT) != OutboxFallback.KIND_UPDATE) {
      state.messages.markStatus(entryID, "failed")
    }
    Log.w(TAG, "Entrada $entryID marcada como fallida: $reason")
  }

  private fun rescue(activeBridge: Bridge, entry: JSONObject, reason: String): Boolean {
    if (entry.optString("packet").isEmpty()) seal(entry)
    if (entry.optString("packet").isEmpty()) return false
    if (entry.optString("upeerId").isEmpty()) return false
    if (!vault.escrow(activeBridge, entry)) return false
    Log.i(TAG, "Entrada ${entry.optString("id")} fuera del envío directo ($reason): queda en custodia del vault")
    return true
  }

  private fun seal(entry: JSONObject) {
    val upeerId = entry.optString("upeerId")
    val peer = state.peerFor(upeerId) ?: return
    val publicKey = peer.optString("publicKey")
    if (publicKey.length != 64) return
    val target = OutboxSealer.Target(
      upeerId,
      peer.optString("address"),
      publicKey,
      state.sessionFor(upeerId),
      peer.optJSONObject("signedPreKey"),
      deviceBindings.bindingFor(peer, upeerId),
    )
    if (target.session == null && sealer.recipientSpk(target) == null) return
    sealer.seal(entry, target)
  }

  private companion object {
    const val TAG = "UPeerTransport"
  }
}
