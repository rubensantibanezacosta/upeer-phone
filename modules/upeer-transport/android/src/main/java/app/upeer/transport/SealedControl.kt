package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONObject

internal object SealedControl {
  fun frame(identity: CryptoIdentity, selfAddress: String, peerPublicKey: String, data: JSONObject): String {
    val signed = identity.buildSignedPacket(selfAddress, data.toString())
    val ciphertext = identity.seal(signed.toByteArray(Charsets.UTF_8), peerPublicKey)
    return JSONObject().put("type", "SEALED").put("ciphertext", ciphertext).toString()
  }
}
