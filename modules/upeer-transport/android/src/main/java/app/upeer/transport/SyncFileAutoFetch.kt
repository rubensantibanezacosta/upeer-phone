package app.upeer.transport

import android.util.Log
import java.io.File

internal class SyncFileAutoFetch(
  private val attachments: () -> List<AttachmentRef>,
  private val stored: (String) -> Boolean,
  private val request: (String) -> Boolean,
  private val isPending: (String) -> Boolean,
  private val progress: (String) -> Float? = { null },
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  private class Attempt(
    val first: Long,
    var last: Long,
    var rounds: Int,
    var unserved: Boolean = false,
    var exhausted: Boolean = false,
  )

  private val lock = Any()
  private val attempts = HashMap<String, Attempt>()
  private var baseline: MutableSet<String>? = null
  private val tracked = LinkedHashSet<String>()
  private val completed = HashSet<String>()

  private var last = SyncFileWork.EMPTY
  private var lastPending = 0
  private var lastFailed = 0

  fun snapshot(): SyncFileWork = synchronized(lock) { last }

  fun tick(): SyncFileWork = synchronized(lock) { run() }

  fun refresh(): SyncFileWork = synchronized(lock) {
    val work = compute(lastPending, lastFailed)
    last = work
    work
  }

  private fun run(): SyncFileWork {
    val now = nowMillis()
    val refs = runCatching { attachments() }.getOrElse { cause ->
      Log.w(TAG, "No se pudieron enumerar los adjuntos sincronizados: ${cause.message ?: cause.javaClass.simpleName}")
      emptyList()
    }
    val seen = LinkedHashMap<String, AttachmentRef>()
    for (ref in refs) seen.putIfAbsent(ref.fileId, ref)
    reviveMissing(seen)
    val known = baseline ?: seen.filterValues { present(it) }.keys.toMutableSet().also { baseline = it }
    var pending = 0
    var failed = 0
    for ((fileId, ref) in seen) {
      if (fileId in known || fileId in completed) continue
      tracked.add(fileId)
      if (present(ref)) {
        completed.add(fileId)
        attempts.remove(fileId)
        continue
      }
      when (step(fileId, now)) {
        Step.QUEUED -> pending += 1
        Step.FAILED -> failed += 1
      }
    }
    for (fileId in tracked) {
      if (fileId in completed || fileId in seen) continue
      if (stored(fileId)) completed.add(fileId)
    }
    lastPending = pending
    lastFailed = failed
    val work = compute(pending, failed)
    last = work
    if (work.known) Log.i(TAG, "Adjuntos por traer de otros dispositivos: ${work.done} de ${work.total}, pendientes ${work.pending}, referencias ${seen.size}")
    return work
  }

  private fun reviveMissing(seen: Map<String, AttachmentRef>) {
    val candidates = HashSet(completed)
    baseline?.let { candidates.addAll(it) }
    for (fileId in candidates) {
      val ref = seen[fileId] ?: continue
      if (present(ref)) continue
      completed.remove(fileId)
      attempts.remove(fileId)
      baseline?.remove(fileId)
      Log.i(TAG, "El adjunto $fileId ya no está en el dispositivo: se vuelve a pedir")
    }
  }

  private fun compute(pending: Int, failed: Int): SyncFileWork {
    if (tracked.isEmpty()) return SyncFileWork.EMPTY
    var units = 0f
    var done = 0
    for (fileId in tracked) {
      if (fileId in completed) {
        done += 1
        units += 1f
        continue
      }
      val partial = runCatching { progress(fileId) }.getOrNull() ?: 0f
      units += partial.coerceIn(0f, 1f)
    }
    return SyncFileWork(
      total = tracked.size,
      done = done,
      pending = pending,
      failed = failed,
      progressUnits = units,
    )
  }

  fun forget(fileId: String) {
    synchronized(lock) { attempts.remove(fileId) }
  }

  fun reset() {
    synchronized(lock) {
      attempts.clear()
      baseline = null
      tracked.clear()
      completed.clear()
      lastPending = 0
      lastFailed = 0
      last = SyncFileWork.EMPTY
    }
  }

  private enum class Step { QUEUED, FAILED }

  private fun step(fileId: String, now: Long): Step {
    if (isPending(fileId)) return Step.QUEUED
    val attempt = attempts[fileId] ?: return dispatch(fileId, now, null)
    if (attempt.exhausted) return Step.FAILED
    val wait = when {
      attempt.unserved -> UNSERVED_RETRY_MS
      attempt.rounds >= FAST_ROUNDS -> SLOW_RETRY_MS
      else -> RETRY_MS
    }
    if (now - attempt.last < wait) return Step.QUEUED
    if (now - attempt.first > MAX_AGE_MS) {
      attempt.exhausted = true
      Log.w(TAG, "Adjunto $fileId sin respuesta tras ${attempt.rounds} intentos; se deja de pedir")
      return Step.FAILED
    }
    return dispatch(fileId, now, attempt)
  }

  private fun dispatch(fileId: String, now: Long, attempt: Attempt?): Step {
    val sent = runCatching { request(fileId) }.getOrElse { cause ->
      Log.w(TAG, "No se pudo pedir el adjunto $fileId: ${cause.message ?: cause.javaClass.simpleName}")
      false
    }
    if (!sent) {
      if (attempt == null) {
        attempts[fileId] = Attempt(now, now, 0, unserved = true)
      } else {
        attempt.last = now
        attempt.unserved = true
      }
      return Step.QUEUED
    }
    if (attempt == null) {
      attempts[fileId] = Attempt(now, now, 1)
    } else {
      attempt.rounds += 1
      attempt.last = now
      attempt.unserved = false
    }
    return Step.QUEUED
  }

  private fun present(ref: AttachmentRef): Boolean {
    if (stored(ref.fileId)) return true
    val path = ref.savedPath ?: return false
    return runCatching { File(path).isFile }.getOrDefault(false)
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val RETRY_MS = 20_000L
    private const val SLOW_RETRY_MS = 60_000L
    private const val UNSERVED_RETRY_MS = 60_000L
    private const val FAST_ROUNDS = 4
    private const val MAX_AGE_MS = 900_000L
  }
}
