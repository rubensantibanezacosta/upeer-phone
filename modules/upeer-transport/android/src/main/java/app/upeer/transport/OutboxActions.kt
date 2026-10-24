package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal class OutboxActions(
  private val link: OutboxLink,
  private val state: ProtocolStateStore,
  private val outbox: OutboxStore,
  private val admission: DeviceAdmissionGuard,
  private val bridge: () -> Bridge?,
  private val isRunning: () -> Boolean,
  private val wake: () -> Unit,
) {
  fun retryChat(upeerId: String, messageID: String): Boolean {
    if (!ID_PATTERN.matches(upeerId)) throw IllegalArgumentException("uPeer ID no válido")
    val message = state.messages.find(messageID) ?: throw IllegalArgumentException("El mensaje ya no existe")
    if (message.optString("upeerId") != upeerId) throw IllegalArgumentException("El mensaje no es de esta conversación")
    if (!message.optBoolean("isMine", false)) throw IllegalArgumentException("Solo se puede reintentar un mensaje propio")
    if (message.optString("status") != "failed") throw IllegalArgumentException("El mensaje no está fallido")
    outbox.enqueue(
      messageID,
      upeerId,
      message.optString("content"),
      message.optString("replyTo"),
      message.optLong("timestamp", System.currentTimeMillis()),
    )
    state.messages.markStatus(messageID, "pending")
    Log.i(TAG, "Mensaje $messageID reencolado para $upeerId")
    wake()
    return true
  }

  fun announceProfile() {
    announcedTo.clear()
  }

  fun announceProfileTo(upeerId: String) {
    if (!isRunning()) return
    val profile = state.profile().toString()
    if (profile.isEmpty() || profile == "{}") return
    if (!announcedTo.add(upeerId)) return
    val peer = state.peerFor(upeerId) ?: return
    if (peer.optString("publicKey").length != 64) return
    val address = peer.optString("address")
    if (address.isBlank()) return
    announcePool.execute {
      val activeBridge = bridge() ?: return@execute
      try {
        link.sendHandshakeRequest(activeBridge, address)
        Log.i(TAG, "Perfil anunciado a $upeerId")
      } catch (cause: Exception) {
        announcedTo.remove(upeerId)
        Log.w(TAG, "No se pudo anunciar el perfil a $upeerId: ${cause.message ?: cause.javaClass.simpleName}")
      }
    }
  }

  fun sendDrReset(upeerId: String) {
    if (!admission.isAdmitted()) return
    val activeBridge = bridge() ?: return
    val peer = state.peerFor(upeerId) ?: return
    val address = peer.optString("address")
    if (address.isBlank()) return
    if (!link.sendDrReset(activeBridge, address, upeerId, peer)) {
      Log.w(TAG, "DR_RESET no enviado a $upeerId")
    }
  }

  private val announcedTo = ConcurrentHashMap.newKeySet<String>()
  private val announcePool = Executors.newCachedThreadPool { task ->
    Thread(task, "upeer-profile-announce").apply { isDaemon = true }
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private val ID_PATTERN = Regex("^[0-9a-fA-F]{64}$")
  }
}
