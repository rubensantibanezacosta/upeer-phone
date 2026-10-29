package app.upeer.transport.vault

internal object VaultQuota {
  const val MIN_SCORE = 30
  const val CASUAL_BYTES = 50L * 1024 * 1024
  const val TRUSTED_BYTES = 500L * 1024 * 1024
  const val INTIMATE_BYTES = 2500L * 1024 * 1024
  const val TRUSTED_SCORE = 65
  const val INTIMATE_SCORE = 80

  fun of(score: Int): Long = when {
    score >= INTIMATE_SCORE -> INTIMATE_BYTES
    score >= TRUSTED_SCORE -> TRUSTED_BYTES
    else -> CASUAL_BYTES
  }
}
