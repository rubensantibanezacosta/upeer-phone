package app.upeer.transport

internal object BlockedPeerList {
  fun normalize(values: List<String>): List<String> =
    values.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

  fun contains(blocked: List<String>, upeerId: String): Boolean =
    blocked.contains(upeerId.trim().lowercase())
}
