package app.upeer.transport

import org.json.JSONObject

internal object SignedPreKeyFields {
  fun valid(value: JSONObject): Boolean =
    value.optString("spkPub").length == 64 &&
      value.optString("spkSig").length == 128 &&
      value.optLong("spkId") > 0

  fun isNewer(current: JSONObject?, candidate: JSONObject): Boolean =
    classify(current, candidate) == SignedPreKeyUpdate.UPDATED

  fun classify(current: JSONObject?, candidate: JSONObject): SignedPreKeyUpdate {
    if (!valid(candidate)) return SignedPreKeyUpdate.INVALID
    return DrResetPolicy.classifySignedPreKey(
      current?.optLong("spkId") ?: 0L,
      current?.optString("spkPub").orEmpty(),
      candidate.optLong("spkId"),
      candidate.optString("spkPub"),
    )
  }
}
