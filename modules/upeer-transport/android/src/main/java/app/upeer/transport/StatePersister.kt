package app.upeer.transport

internal class StatePersister(
  private val lock: Any,
  private val snapshot: () -> ByteArray,
  private val write: (ByteArray) -> Unit,
  private val onError: (Exception) -> Unit,
) {
  private val gate = Object()
  private var dirty = false
  private var closed = false
  private var generation = 0L
  private var writtenGeneration = 0L
  private var worker: Thread? = null

  fun request() {
    synchronized(gate) {
      dirty = true
      generation += 1
      ensureWorker()
      gate.notifyAll()
    }
  }

  fun flush(timeoutMillis: Long = FLUSH_TIMEOUT_MS): Boolean {
    synchronized(gate) {
      if (!dirty && writtenGeneration >= generation) return true
      val target = generation
      ensureWorker()
      gate.notifyAll()
      val deadline = System.currentTimeMillis() + timeoutMillis
      while (writtenGeneration < target) {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) return false
        gate.wait(remaining)
      }
      return true
    }
  }

  fun close(timeoutMillis: Long = FLUSH_TIMEOUT_MS) {
    val current = synchronized(gate) {
      closed = true
      gate.notifyAll()
      worker
    }
    current?.join(timeoutMillis)
  }

  private fun ensureWorker() {
    val active = worker
    if (active != null && active.isAlive) {
      closed = false
      return
    }
    closed = false
    val thread = Thread({ run() }, WORKER_NAME)
    thread.isDaemon = true
    worker = thread
    thread.start()
  }

  private fun run() {
    while (true) {
      val target = synchronized(gate) {
        while (!dirty && !closed) gate.wait()
        if (closed && !dirty) {
          worker = null
          return
        }
        dirty = false
        generation
      }
      coalesce()
      val bytes = synchronized(lock) { snapshot() }
      try {
        write(bytes)
      } catch (cause: Exception) {
        onError(cause)
      }
      synchronized(gate) {
        if (target > writtenGeneration) writtenGeneration = target
        gate.notifyAll()
      }
    }
  }

  private fun coalesce() {
    synchronized(gate) {
      if (!closed) gate.wait(COALESCE_MS)
    }
  }

  private companion object {
    const val WORKER_NAME = "upeer-state-writer"
    const val FLUSH_TIMEOUT_MS = 5_000L
    const val COALESCE_MS = 150L
  }
}
