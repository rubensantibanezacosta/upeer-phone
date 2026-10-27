package app.upeer.transport

import org.json.JSONObject

internal object TypingIndicators {
  const val WINDOW_MS = 6_000L
  const val STATE_TYPING = "typing"
  const val STATE_RECORDING = "recording"
  const val STATE_STOP = "stop"

  fun stateOf(value: String?): String = when (value) {
    STATE_RECORDING -> STATE_RECORDING
    STATE_STOP -> STATE_STOP
    else -> STATE_TYPING
  }

  fun expiresAt(nowMillis: Long): Long = nowMillis + WINDOW_MS

  fun isActive(untilMillis: Long, nowMillis: Long): Boolean = untilMillis > nowMillis

  fun data(state: String = STATE_TYPING): JSONObject =
    JSONObject().put("type", "TYPING").put("state", stateOf(state))
}
