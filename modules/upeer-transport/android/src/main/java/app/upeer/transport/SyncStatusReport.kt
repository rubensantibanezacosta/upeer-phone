package app.upeer.transport

import org.json.JSONObject

internal object SyncStatusReport {
  const val STATE_IDLE = "idle"
  const val STATE_SYNCING = "syncing"
  const val STATE_LIVE = "live"
  const val KIND_ATTACHMENT = "attachment"
  const val GATE_BOOT = "boot"
  const val GATE_OPEN = "open"

  fun json(
    progress: List<SyncHistoryProgress>,
    peers: List<SyncPeerRecord>,
    pendingEvents: Int,
    unsupported: Int,
    nowMillis: Long,
    files: SyncFileWork = SyncFileWork.EMPTY,
    unsupportedKinds: JSONObject = JSONObject(),
    gate: Boolean = false,
  ): JSONObject {
    val lastSeen = peers.maxOfOrNull { it.lastSeen } ?: 0L
    val stale = peers.isEmpty() || lastSeen <= 0L || nowMillis - lastSeen > STALE_MS
    val filesBusy = files.pending > 0 || files.total > files.done + files.failed
    val historyBusy = progress.any { !it.done && it.known }
    val busy = !stale && (filesBusy || historyBusy || pendingEvents > 0)
    val state = when {
      peers.isEmpty() && progress.isEmpty() -> STATE_IDLE
      busy -> STATE_SYNCING
      else -> STATE_LIVE
    }
    val booting = gate && (progress.isEmpty() || (!stale && progress.any { !it.done }))
    return JSONObject()
      .put("state", state)
      .put("gate", if (booting) GATE_BOOT else GATE_OPEN)
      .put("devices", peers.size)
      .put("devicesDone", progress.count { it.done })
      .put("pendingEvents", pendingEvents)
      .put("unsupported", unsupported)
      .put("unsupportedKinds", JSONObject(unsupportedKinds.toString()))
      .put("lastPeerAt", lastSeen)
      .put("ageMillis", if (peers.isEmpty()) 0L else nowMillis - lastSeen)
      .put("progress", SyncProgress.json(progress, pendingEvents, files, state))
  }

  private const val STALE_MS = 45_000L
}
