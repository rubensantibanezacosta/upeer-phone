package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class CallInbound(
  private val store: CallStore,
  private val send: (String, JSONObject, String) -> Unit,
  private val onIncoming: (CallSession) -> Unit = {},
  private val now: () -> Long = System::currentTimeMillis,
  /**
   * Arranca la media de una llamada ya conectada. Por defecto no hace nada para que las pruebas de
   * señalización sigan corriendo en JVM sin micrófono ni códec.
   */
  private val onConnected: (CallSession) -> Unit = {},
  /** Un frame de media que llegó del otro extremo. */
  private val onMedia: (JSONObject) -> Unit = {},
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
      // `answered-elsewhere`: otro dispositivo de la misma cuenta cogió la llamada, así que aquí se
      // para el timbre pero no se escribe una «perdida» falsa. El motivo viaja en el paquete desde el
      // 08/11; antes se leía como `remote-end` y el historial no distinguía los dos casos.
      CallPackets.END -> store.end(
        callId,
        if (packet.optString("reason") == CallEndReasons.ANSWERED_ELSEWHERE) {
          CallEndReasons.ANSWERED_ELSEWHERE
        } else {
          CallEndReasons.REMOTE_END
        },
      )
      CallPackets.MEDIA_UPDATE -> onMediaUpdate(packet, callId)
      CallPackets.MEDIA -> onMedia(packet)
      // SDP, ICE y META describen la negociación de WebRTC del escritorio. El móvil usa MediaCodec con
      // un formato fijo (Opus 48 kHz mono), así que no hay nada que negociar y se registran y se siguen.
      CallPackets.SDP, CallPackets.ICE, CallPackets.META, CallPackets.JOIN, CallPackets.LEAVE ->
        Log.i(TAG, "$type de $senderUpeerId registrado y no aplicado: el móvil no negocia media")
    }
  }

  private fun onOffer(packet: JSONObject, senderUpeerId: String, callId: String) {
    if (CallPackets.groupMembers(packet).isNotEmpty()) {
      Log.i(TAG, "CALL_OFFER de grupo de $senderUpeerId ignorada: la llamada de grupo aún no existe en el móvil")
      return
    }
    // Ojo con `busy()` a secas: con varios dispositivos de la misma cuenta, la oferta de esta misma
    // llamada llega también aquí, y responder CALL_BUSY colgaría la llamada que el otro dispositivo
    // del usuario ya tenía en marcha. Solo es ocupado si la llamada viva es otra distinta.
    if (store.busyWithOther(callId)) {
      val current = store.active()
      Log.i(TAG, "CALL_OFFER de $senderUpeerId rechazada: ya hay una llamada con ${current?.peerUpeerId ?: "otro contacto"}")
      send(senderUpeerId, CallPackets.busy(callId, now()), CallPackets.BUSY)
      return
    }
    // La misma llamada que ya está aquí (otro dispositivo de mi cuenta la timbró o la contestó): no
    // se crea una segunda sesión ni se vuelve a mandar RING.
    if (store.active()?.callId == callId) {
      Log.i(TAG, "CALL_OFFER repetida de $senderUpeerId para $callId: ya está en curso en este dispositivo")
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
    store.active()?.let(onConnected)
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
