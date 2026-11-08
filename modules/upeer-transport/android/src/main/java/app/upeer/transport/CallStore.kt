package app.upeer.transport

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Estado de la llamada activa. Sigue el mismo autómata que `callManager` del escritorio:
 * timbre, negociación, conectada y terminada, con los plazos de cada fase y un único
 * evento escrito al cerrar. La media todavía no existe, así que aquí solo vive la
 * señalización.
 */
internal class CallStore(
  private val now: () -> Long = System::currentTimeMillis,
  private val onEnded: (CallSession) -> Unit = {},
) {
  private val lock = Any()
  private val timers = mutableMapOf<String, ScheduledFuture<*>>()
  private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "upeer-call").apply { isDaemon = true }
  }
  private var session: CallSession? = null

  fun active(): CallSession? = synchronized(lock) { session?.takeIf { it.live() } }

  fun snapshotJSON(): String {
    val current = synchronized(lock) { session } ?: return "{}"
    if (current.live()) return current.json().toString()
    val visible = current.endedAt > 0L && now() - current.endedAt <= ENDED_VISIBLE_MS
    return if (visible) current.json().toString() else "{}"
  }

  fun busy(): Boolean = active() != null

  /**
   * ¿La llamada viva es esta misma, venga de donde venga?
   *
   * Con la cuenta abierta en varios dispositivos, la oferta suena en todos con **el mismo `callId`**.
   * Sin esta comprobación, el segundo dispositivo pregunta `busy()`, ve la llamada del primero y
   * responde `CALL_BUSY` a su propio llamante, que colgaría la llamada que ya estaba en marcha. Solo
   * es «ocupado» cuando la llamada viva es **otra**.
   */
  fun busyWithOther(callId: String): Boolean {
    val current = active() ?: return false
    return current.callId != callId
  }

  fun start(peerUpeerId: String, kind: String, outgoing: Boolean, callId: String? = null): CallSession? {
    synchronized(lock) {
      if (busy()) return null
      val created = CallSession(
        callId = callId?.takeIf(CallIds::valid) ?: CallIds.generate(),
        peerUpeerId = peerUpeerId.lowercase(),
        kind = CallKinds.of(kind),
        outgoing = outgoing,
        phase = if (outgoing) CallPhases.OUTGOING else CallPhases.INCOMING,
        muted = false,
        cameraEnabled = CallKinds.of(kind) == CallKinds.VIDEO,
        startedAt = now(),
        connectedAt = 0L,
        endedAt = 0L,
        endReason = "",
        eventWritten = false,
      )
      session = created
      arm(created.callId, RING_TIMEOUT_MS, CallEndReasons.NO_ANSWER)
      return created
    }
  }

  fun accept(callId: String): Boolean = transition(callId, setOf(CallPhases.INCOMING, CallPhases.OUTGOING)) { current ->
    current.phase = CallPhases.NEGOTIATING
    arm(callId, NEGOTIATE_TIMEOUT_MS, CallEndReasons.ERROR)
    true
  }

  fun connect(callId: String): Boolean = transition(callId, setOf(CallPhases.NEGOTIATING, CallPhases.OUTGOING, CallPhases.INCOMING)) { current ->
    clearTimer(callId)
    current.phase = CallPhases.CONNECTED
    current.connectedAt = now()
    true
  }

  fun end(callId: String, reason: String, notifyEnded: Boolean = true): Boolean {
    val ended = synchronized(lock) {
      val current = session?.takeIf { it.callId == callId && it.live() } ?: return false
      clearTimer(callId)
      current.phase = CallPhases.ENDED
      current.endedAt = now()
      current.endReason = reason
      current
    }
    if (notifyEnded) notifyEnded(ended)
    return true
  }

  fun setMuted(callId: String, muted: Boolean): Boolean = transition(callId, TALKING_PHASES) { current ->
    current.muted = muted
    true
  }

  fun setCamera(callId: String, enabled: Boolean): Boolean = transition(callId, TALKING_PHASES) { current ->
    if (current.kind != CallKinds.VIDEO) return@transition false
    current.cameraEnabled = enabled
    true
  }

  fun drop(callId: String) {
    synchronized(lock) {
      if (session?.callId == callId) session = null
      clearTimer(callId)
    }
  }

  fun resetForTests() {
    synchronized(lock) {
      timers.values.forEach { it.cancel(false) }
      timers.clear()
      session = null
    }
  }

  private fun transition(callId: String, phases: Set<String>, update: (CallSession) -> Boolean): Boolean {
    val changed = synchronized(lock) {
      val current = session?.takeIf { it.callId == callId && it.phase in phases } ?: return false
      update(current)
    }
    return changed
  }

  private fun notifyEnded(ended: CallSession) {
    if (ended.eventWritten) return
    ended.eventWritten = true
    try {
      onEnded(ended)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo cerrar el evento de la llamada ${ended.callId}: ${cause.message}")
    }
  }

  private fun arm(callId: String, millis: Long, reason: String) {
    clearTimer(callId)
    timers[callId] = scheduler.schedule({
      if (end(callId, reason)) Log.i(TAG, "Llamada $callId cerrada por plazo ($reason)")
    }, millis, TimeUnit.MILLISECONDS)
  }

  private fun clearTimer(callId: String) {
    timers.remove(callId)?.cancel(false)
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val RING_TIMEOUT_MS = 30_000L
    private const val NEGOTIATE_TIMEOUT_MS = 20_000L
    private const val ENDED_VISIBLE_MS = 4_000L
    private val TALKING_PHASES = setOf(
      CallPhases.OUTGOING,
      CallPhases.INCOMING,
      CallPhases.NEGOTIATING,
      CallPhases.CONNECTED,
    )
  }
}
