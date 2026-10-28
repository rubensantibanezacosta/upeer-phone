package app.upeer.transport

import org.json.JSONObject

internal object CallPhases {
  const val OUTGOING = "outgoing-ringing"
  const val INCOMING = "incoming-ringing"
  const val NEGOTIATING = "negotiating"
  const val CONNECTED = "connected"
  const val ENDED = "ended"

  fun isLive(phase: String): Boolean = phase != ENDED
}

internal object CallKinds {
  const val AUDIO = "audio"
  const val VIDEO = "video"

  fun of(value: String?): String = if (value == VIDEO) VIDEO else AUDIO
  fun label(kind: String): String = if (kind == VIDEO) "Videollamada" else "Llamada de voz"
}

internal object CallEndReasons {
  const val LOCAL_HANGUP = "local-hangup"
  const val REMOTE_END = "remote-end"
  const val REJECTED = "rejected"
  const val BUSY = "busy"
  const val CANCELED = "canceled"
  const val NO_ANSWER = "no-answer"
  const val ERROR = "error"
}

internal data class CallSession(
  val callId: String,
  val peerUpeerId: String,
  val kind: String,
  val outgoing: Boolean,
  var phase: String,
  var muted: Boolean,
  var cameraEnabled: Boolean,
  val startedAt: Long,
  var connectedAt: Long,
  var endedAt: Long,
  var endReason: String,
  var eventWritten: Boolean,
) {
  fun live(): Boolean = CallPhases.isLive(phase)

  fun json(): JSONObject = JSONObject()
    .put("callId", callId)
    .put("peerUpeerId", peerUpeerId)
    .put("kind", kind)
    .put("direction", if (outgoing) "outgoing" else "incoming")
    .put("phase", phase)
    .put("muted", muted)
    .put("cameraEnabled", cameraEnabled)
    .put("startedAt", startedAt)
    .put("connectedAt", connectedAt)
    .put("endedAt", endedAt)
    .put("endReason", endReason)

  fun durationSeconds(): Long =
    if (connectedAt <= 0L) 0L else ((endedAt.takeIf { it > 0L } ?: connectedAt) - connectedAt) / 1000L
}

internal object CallIds {
  private const val HEX = "0123456789abcdef"

  fun generate(): String {
    val builder = StringBuilder(32)
    repeat(32) { builder.append(HEX[(Math.random() * 16).toInt()]) }
    return builder.toString()
  }

  fun valid(value: String): Boolean = value.length in 1..128 && value.all { it.isLetterOrDigit() || it == '-' }

  fun eventMessageId(callId: String): String {
    val normalized = callId.filter { it in HEX }.padEnd(32, '0').take(32)
    return "${normalized.substring(0, 8)}-${normalized.substring(8, 12)}-4${normalized.substring(13, 16)}-a${normalized.substring(17, 20)}-${normalized.substring(20, 32)}"
  }
}
