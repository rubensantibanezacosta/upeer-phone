package app.upeer.transport

import java.security.MessageDigest

internal object SafetyNumber {
  private const val KEY_LENGTH = 64
  private const val GROUPS = 8
  private const val GROUP_SIZE = 5
  private const val DIGEST_BYTES = GROUPS * GROUP_SIZE / 2

  fun compute(firstKey: String, secondKey: String): String {
    val first = firstKey.trim().lowercase()
    val second = secondKey.trim().lowercase()
    if (!valid(first) || !valid(second)) return ""
    val ordered = listOf(first, second).sorted()
    val digest = MessageDigest.getInstance("SHA-256").digest((ordered[0] + ordered[1]).toByteArray(Charsets.UTF_8))
    val hex = digest.copyOf(DIGEST_BYTES).joinToString("") { "%02X".format(it) }
    return hex.chunked(GROUP_SIZE).joinToString(" ")
  }

  private fun valid(key: String): Boolean =
    key.length == KEY_LENGTH && key.all { it in '0'..'9' || it in 'a'..'f' }
}
