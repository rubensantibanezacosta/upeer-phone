package app.upeer.transport.dht

object DhtSequence {
  const val MAX_JUMP = 24L * 60L * 60L * 1000L

  data class Validation(val valid: Boolean, val requiresPoW: Boolean, val reason: String)

  fun validate(currentSeq: Long, candidateSeq: Long): Validation {
    require(currentSeq >= 0 && candidateSeq >= 0) { "dhtSeq must not be negative" }
    if (candidateSeq < currentSeq) {
      return Validation(false, false, "Sequence rollback detected")
    }
    if (candidateSeq == currentSeq) {
      return Validation(true, false, "Sequence identical")
    }
    if (currentSeq == 0L) {
      return Validation(true, false, "")
    }
    val jump = candidateSeq - currentSeq
    if (jump > MAX_JUMP) {
      return Validation(false, true, "Sequence jump too large: $jump > $MAX_JUMP")
    }
    return Validation(true, false, "")
  }

  fun next(currentSeq: Long, lastAddress: String, address: String): Long {
    require(currentSeq >= 0) { "dhtSeq must not be negative" }
    if (lastAddress == address) return currentSeq
    require(currentSeq < Long.MAX_VALUE) { "dhtSeq overflow" }
    return currentSeq + 1
  }
}
