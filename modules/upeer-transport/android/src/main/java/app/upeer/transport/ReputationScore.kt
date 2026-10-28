package app.upeer.transport

import kotlin.math.roundToInt

internal object ReputationScore {
  const val NEUTRAL = 50

  fun of(
    subject: String,
    vouches: List<ReputationVouch>,
    directContacts: Set<String>,
    now: Long,
  ): Int {
    val id = subject.trim().lowercase()
    if (!ReputationVouch.isAccountSubject(id)) return NEUTRAL
    if (directContacts.isEmpty()) return NEUTRAL
    val since = now - ReputationVouch.THIRTY_DAYS_MS
    val subjectVouches = vouches.filter { it.toId == id && it.timestamp >= since }
    if (subjectVouches.isEmpty()) return NEUTRAL
    val counted = HashMap<String, Int>()
    var delta = 0.0
    for (vouch in subjectVouches.sortedBy { it.timestamp }) {
      if (vouch.fromId !in directContacts) continue
      val used = counted[vouch.fromId] ?: 0
      if (used >= ReputationVouch.MAX_PER_SENDER) continue
      counted[vouch.fromId] = used + 1
      val weight = ReputationVouch.weight(vouch.type)
      delta += if (vouch.positive) weight else -weight
    }
    return (NEUTRAL + delta).coerceIn(0.0, 100.0).roundToInt()
  }
}
