package app.upeer.transport

internal class DeviceAdmissionException : IllegalStateException("device is not admitted by the current account registry")

internal class DeviceAdmissionGuard(
  private val check: () -> Boolean?,
  private val onRevoked: () -> Unit,
) {
  private val lock = Any()
  private var revoked = false

  fun isAdmitted(): Boolean {
    val admitted = check()
    if (admitted == true) {
      synchronized(lock) { revoked = false }
      return true
    }
    if (admitted == null) return true
    notifyRevoked()
    return false
  }

  fun ensure(ensureAdmission: () -> Unit) {
    try {
      ensureAdmission()
    } catch (cause: DeviceAdmissionException) {
      require()
      throw cause
    }
    require()
  }

  fun require() {
    if (!isAdmitted()) throw DeviceAdmissionException()
  }

  private fun notifyRevoked() {
    val first = synchronized(lock) {
      if (revoked) {
        false
      } else {
        revoked = true
        true
      }
    }
    if (first) onRevoked()
  }
}
