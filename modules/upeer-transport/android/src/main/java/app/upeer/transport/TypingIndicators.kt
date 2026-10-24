package app.upeer.transport

import org.json.JSONObject

internal object TypingIndicators {
  const val WINDOW_MS = 6_000L

  fun expiresAt(nowMillis: Long): Long = nowMillis + WINDOW_MS

  fun isActive(untilMillis: Long, nowMillis: Long): Boolean = untilMillis > nowMillis

  fun data(): JSONObject = JSONObject().put("type", "TYPING")
}
