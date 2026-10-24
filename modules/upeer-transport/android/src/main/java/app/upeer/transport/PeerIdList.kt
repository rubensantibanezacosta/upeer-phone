package app.upeer.transport

internal object PeerIdList {
  fun normalize(values: List<String>): List<String> =
    values.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

  fun contains(ids: List<String>, upeerId: String): Boolean =
    ids.contains(upeerId.trim().lowercase())
}
