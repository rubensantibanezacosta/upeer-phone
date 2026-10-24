package app.upeer.transport

internal object ProfileFields {
  const val MAX_ALIAS_LENGTH = 64
  const val MAX_AVATAR_BYTES = 2_000_000

  fun alias(value: String): String = value.trim().take(MAX_ALIAS_LENGTH)

  fun avatar(value: String): String {
    val trimmed = value.trim()
    if (!trimmed.startsWith("data:image/")) return ""
    if (trimmed.length > MAX_AVATAR_BYTES) return ""
    return trimmed
  }
}
