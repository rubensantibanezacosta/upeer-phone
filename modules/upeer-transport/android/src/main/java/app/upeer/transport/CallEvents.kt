package app.upeer.transport

import android.util.Log
import org.json.JSONObject

/**
 * Resumen de una llamada terminada, con el mismo formato que usa el escritorio para que
 * el historial de los dos clientes se lea igual: `__SYS__|Llamada de voz · 00:35 · 1793…`
 * cuando hubo conversación, y `perdida` o `rechazada` cuando no la hubo.
 */
internal object CallEvent {
  const val LABEL_PREFIX = "__SYS__|"

  fun summary(session: CallSession): String? {
    val label = CallKinds.label(session.kind)
    if (session.connectedAt > 0L) {
      val seconds = session.durationSeconds()
      return "$label · ${duration(seconds)} · ${session.connectedAt}"
    }
    if (session.outgoing) return "$label perdida"
    if (session.endReason == CallEndReasons.REJECTED) return "$label rechazada"
    return null
  }

  /** El escritorio avisa al otro extremo cuando la llamada salió y no fue rechazada, y cuando entró y se atendió. */
  fun notifiesPeer(session: CallSession): Boolean =
    (session.outgoing && session.endReason != CallEndReasons.REJECTED) ||
      (!session.outgoing && session.connectedAt > 0L)

  fun duration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return "${(safe / 60).toString().padStart(2, '0')}:${(safe % 60).toString().padStart(2, '0')}"
  }
}

internal class CallEvents(
  private val messages: ProtocolMessageList,
  private val enqueue: (String, String, String) -> Unit,
) {
  fun onEnded(session: CallSession) {
    val summary = CallEvent.summary(session) ?: return
    val content = CallEvent.LABEL_PREFIX + summary
    val messageId = CallIds.eventMessageId(session.callId)
    if (CallEvent.notifiesPeer(session) && session.peerUpeerId.isNotEmpty()) {
      enqueue(session.peerUpeerId, content, messageId)
      return
    }
    try {
      messages.save(
        JSONObject()
          .put("id", messageId)
          .put("upeerId", session.peerUpeerId)
          .put("isMine", true)
          .put("content", content)
          .put("status", "delivered")
          .put("version", 1)
          .put("isEdited", false)
          .put("timestamp", session.endedAt.takeIf { it > 0L } ?: System.currentTimeMillis()),
      )
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo guardar el evento de la llamada ${session.callId}: ${cause.message}")
    }
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
