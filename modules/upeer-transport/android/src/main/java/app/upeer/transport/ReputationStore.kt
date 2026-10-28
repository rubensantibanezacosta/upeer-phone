package app.upeer.transport

import org.json.JSONObject

internal class ReputationStore(
  private val stateProvider: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun all(): List<ReputationVouch> = synchronized(lock) { read(stateProvider(), System.currentTimeMillis()) }

  fun ids(): Set<String> = all().mapTo(LinkedHashSet()) { it.id }

  fun missing(theirIds: List<String>, now: Long): List<String> {
    val ours = ids()
    return theirIds
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() && it !in ours }
      .distinct()
      .take(REQUEST_LIMIT)
  }

  fun accept(senderId: String, incoming: List<ReputationVouch>, now: Long): Int {
    val sender = senderId.trim().lowercase()
    if (sender.length != 64) return 0
    return synchronized(lock) {
      val state = stateProvider()
      val existing = read(state, now).associateByTo(LinkedHashMap()) { it.id }
      val since = now - ReputationVouch.ONE_DAY_MS
      val perSenderToday = HashMap<String, Int>()
      for (vouch in existing.values) {
        if (vouch.timestamp < since) continue
        perSenderToday[vouch.fromId] = (perSenderToday[vouch.fromId] ?: 0) + 1
      }
      var accepted = 0
      for (raw in incoming) {
        val vouch = ReputationVouch.parse(raw.toJson(), now) ?: continue
        if (vouch.fromId != sender) continue
        if (existing.containsKey(vouch.id)) continue
        val used = perSenderToday[sender] ?: 0
        if (used >= ReputationVouch.MAX_PER_SENDER_PER_DAY) continue
        existing[vouch.id] = vouch
        perSenderToday[sender] = used + 1
        accepted += 1
      }
      if (accepted == 0) return@synchronized 0
      write(state, existing.values.toList())
      persist()
      accepted
    }
  }

  fun deliverable(ids: List<String>): List<ReputationVouch> {
    if (ids.isEmpty()) return emptyList()
    val wanted = ids.map { it.trim().lowercase() }.toSet()
    return all()
      .filter { it.id in wanted }
      .sortedByDescending { it.timestamp }
      .take(ReputationVouch.DELIVER_MAX_VOUCHES)
  }

  fun scoresJSON(subjects: List<String>, directContacts: List<String>, now: Long): String {
    val vouches = all()
    val direct = directContacts.mapTo(HashSet()) { it.trim().lowercase() }
    val result = JSONObject()
    for (subject in subjects) {
      val id = subject.trim().lowercase()
      if (id.isEmpty()) continue
      result.put(id, ReputationScore.of(id, vouches, direct, now))
    }
    return result.toString()
  }

  private fun read(state: JSONObject, now: Long): List<ReputationVouch> {
    val raw = state.optJSONObject(KEY)?.optJSONObject(VOUCHES) ?: return emptyList()
    val parsed = ArrayList<ReputationVouch>(raw.length())
    for (key in raw.keys()) {
      ReputationVouch.parse(raw.optJSONObject(key), now)?.let(parsed::add)
    }
    return parsed.sortedBy { it.timestamp }
  }

  private fun write(state: JSONObject, vouches: List<ReputationVouch>) {
    val kept = vouches.sortedByDescending { it.timestamp }.take(MAX_STORED)
    val map = JSONObject()
    for (vouch in kept) map.put(vouch.id, vouch.toJson())
    val root = state.optJSONObject(KEY) ?: JSONObject()
    root.put(VOUCHES, map)
    state.put(KEY, root)
  }

  private companion object {
    const val KEY = "reputation"
    const val VOUCHES = "vouches"
    const val MAX_STORED = 2_000
    const val REQUEST_LIMIT = 50
  }
}
