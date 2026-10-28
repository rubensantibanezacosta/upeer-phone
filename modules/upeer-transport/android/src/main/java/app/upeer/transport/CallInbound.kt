package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class CallInbound(
  private val store: CallStore,
  private val send: (String, JSONObject, String) -> Unit,
  private val onIncoming: (CallSession) -> Unit = {},
  private val now: () -> Long = System::currentTimeMillis,
) {
  fun handle(packet: JSONObject, senderUpeerId: String, type: String) {
    if (!CallPackets.valid(packet)) {
      Log.w(TAG, "Paquete $type de $senderUpeerId descartado: campos inválidos")
      return
    }
    val callId = packet.optString("callId")
    when (type) {
      CallPackets.OFFER -> onOffer(packet, senderUpeerId, callId)
      CallPackets.RING -> Log.i(TAG, "Timbre confirmado para $callId")
      CallPackets.ACCEPT -> onAccept(callId)
      CallPackets.REJECT -> store.end(callId, CallEndReasons.REJECTED)
      CallPackets.BUSY -> store.end(callId, CallEndReasons.BUSY)
      CallPackets.CANCEL -> store.end(callId, CallEndReasons.CANCELED)
      CallPackets.END -> store.end(callId, CallEndReasons.REMOTE_END)
      CallPackets.MEDIA_UPDATE -> onMediaUpdate(packet, callId)
      CallPackets.MEDIA, CallPackets.SDP, CallPackets.ICE, CallPackets.META, CallPackets.JOIN, CallPackets.LEAVE ->
        Log.i(TAG, "$type de $senderUpeerId ignorado: este cliente todavía no transporta media")
    }
  }

  private fun onOffer(packet: JSONObject, senderUpeerId: String, callId: String) {
    if (CallPackets.groupMembers(packet).isNotEmpty()) {
      Log.i(TAG, "CALL_OFFER de grupo de $senderUpeerId ignorada: la llamada de grupo aún no existe en el móvil")
      return
    }
    if (store.busy()) {
      val current = store.active()
      Log.i(TAG, "CALL_OFFER de $senderUpeerId rechazada: ya hay una llamada con ${current?.peerUpeerId ?: "otro contacto"}")
      send(senderUpeerId, CallPackets.busy(callId, now()), CallPackets.BUSY)
      return
    }
    val session = store.start(senderUpeerId, packet.optString("kind"), outgoing = false, callId = callId) ?: return
    send(senderUpeerId, CallPackets.ring(session.callId, now()), CallPackets.RING)
    onIncoming(session)
  }

  private fun onAccept(callId: String) {
    if (store.active()?.outgoing != true) return
    store.accept(callId)
    store.connect(callId)
  }

  private fun onMediaUpdate(packet: JSONObject, callId: String) {
    val current = store.active() ?: return
    if (current.callId != callId) return
    Log.i(
      TAG,
      "Estado remoto de $callId: silencio=${packet.optBoolean("muted")} cámara=${packet.optBoolean("cameraEnabled")}",
    )
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
