package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class GroupControlSealer(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
) {
  fun context(groupId: String): String = "${GROUP_CONTEXT_PREFIX}$groupId"

  fun seal(groupId: String, targetUpeerId: String, payload: String): JSONObject? {
    val current = identity.current() ?: return null
    if (payload.isEmpty()) return null
    val peer = state.peerFor(targetUpeerId) ?: return null
    val peerPublicKey = peer.optString("publicKey")
    if (!VALID_KEY.matches(peerPublicKey)) return null
    val context = context(groupId)
    val fields = JSONObject()
    val session = state.sessionFor(targetUpeerId, context).orEmpty()
    val activeSession = if (session.isEmpty()) {
      val signedPreKey = peer.optJSONObject("signedPreKey") ?: return null
      val spkId = signedPreKey.optLong("spkId")
      val spkPublicKey = signedPreKey.optString("spkPub")
      if (spkId <= 0 || !VALID_KEY.matches(spkPublicKey)) return null
      val started = runCatching {
        JSONObject(current.startOutboundSession(peerPublicKey, spkPublicKey, spkId))
      }.getOrElse { cause ->
        Log.w(TAG, "No se pudo abrir la sesión de grupo con $targetUpeerId: ${message(cause)}")
        return null
      }
      started.optString("x3dhInit").takeIf(String::isNotEmpty)?.let {
        runCatching { fields.put("x3dhInit", JSONObject(it)) }
      }
      started.optString("session")
    } else {
      session
    }
    if (activeSession.isEmpty()) return null
    val encrypted = runCatching {
      JSONObject(current.ratchetEncrypt(activeSession, payload.toByteArray(Charsets.UTF_8)))
    }.getOrElse { cause ->
      Log.w(TAG, "No se pudo cifrar el control de grupo para $targetUpeerId: ${message(cause)}")
      return null
    }
    val nextSession = encrypted.optString("session")
    val message = runCatching { JSONObject(encrypted.optString("message")) }.getOrNull() ?: return null
    val header = message.optJSONObject("header") ?: return null
    val ciphertext = message.optString("ciphertext")
    val nonce = message.optString("nonce")
    if (nextSession.isEmpty() || ciphertext.isEmpty() || nonce.isEmpty()) return null
    fields.put("payload", ciphertext)
    fields.put("nonce", nonce)
    fields.put(
      "ratchetHeader",
      JSONObject()
        .put("dh", header.optString("dh"))
        .put("pn", header.optLong("pn"))
        .put("n", header.optLong("n")),
    )
    state.saveSession(targetUpeerId, nextSession, context)
    return fields
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  companion object {
    private const val TAG = "UPeerTransport"
    private const val GROUP_CONTEXT_PREFIX = "group:"
    private val VALID_KEY = Regex("^[0-9a-fA-F]{64}$")
  }
}
