package app.upeer.transport

import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal object GroupMessageCrypto {
  private val SENDER_KEY = Regex("^[0-9a-f]{64}$")
  private val NONCE = Regex("^[0-9a-f]{48}$")
  private val CIPHERTEXT = Regex("^[0-9a-f]{66,}$")

  fun validSenderKey(value: String): Boolean = SENDER_KEY.matches(value)

  fun seal(senderKey: String, plaintext: String): JSONObject? {
    if (!validSenderKey(senderKey)) return null
    val encoded = runCatching { Transportbridge.sealGroupPayload(senderKey, plaintext) }.getOrNull() ?: return null
    val sealed = runCatching { JSONObject(encoded) }.getOrNull() ?: return null
    val nonce = sealed.optString("nonce")
    val ciphertext = sealed.optString("ciphertext")
    if (!NONCE.matches(nonce) || !CIPHERTEXT.matches(ciphertext)) return null
    return sealed
  }

  fun open(senderKey: String, nonce: String, ciphertext: String): String? {
    if (!validSenderKey(senderKey) || !NONCE.matches(nonce) || !CIPHERTEXT.matches(ciphertext)) return null
    return runCatching { Transportbridge.openGroupPayload(senderKey, nonce, ciphertext) }.getOrNull()
  }
}
