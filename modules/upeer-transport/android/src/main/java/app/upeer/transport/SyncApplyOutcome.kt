package app.upeer.transport

internal enum class SyncApplyOutcome {
  APPLIED,
  UNCHANGED,
  UNSUPPORTED,
  REJECTED,
}
