package app.upeer.transport

import org.json.JSONObject

internal object SyncStatusReport {
  const val STATE_IDLE = "idle"
  const val STATE_SYNCING = "syncing"
  const val STATE_LIVE = "live"

  fun json(
    progress: List<SyncHistoryProgress>,
    peers: List<SyncPeerRecord>,
    pendingEvents: Int,
    unsupported: Int,
    nowMillis: Long,
  ): JSONObject {
    val done = progress.isNotEmpty() && progress.all { it.done }
    val lastSeen = peers.maxOfOrNull { it.lastSeen } ?: 0L
    val stale = peers.isEmpty() || lastSeen <= 0L || nowMillis - lastSeen > STALE_MS
    val state = when {
      peers.isEmpty() && progress.isEmpty() -> STATE_IDLE
      !stale && done.not() -> STATE_SYNCING
      else -> STATE_LIVE
    }
    return JSONObject()
      .put("state", state)
      .put("devices", peers.size)
      .put("devicesDone", progress.count { it.done })
      .put("pendingEvents", pendingEvents)
      .put("unsupported", unsupported)
      .put("lastPeerAt", lastSeen)
      .put("ageMillis", if (peers.isEmpty()) 0L else nowMillis - lastSeen)
  }

  private const val STALE_MS = 45_000L
}
