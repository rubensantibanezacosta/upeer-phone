package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONObject

internal object SealedControl {
  fun frame(identity: CryptoIdentity, selfAddress: String, peerPublicKey: String, data: JSONObject): String =
    frame(identity, null, selfAddress, peerPublicKey, data)

  fun frame(
    identity: CryptoIdentity,
    device: DeviceIdentity?,
    selfAddress: String,
    peerPublicKey: String,
    data: JSONObject,
  ): String = frame(identity, device, selfAddress, peerPublicKey, data, null)

  fun frame(
    identity: CryptoIdentity,
    device: DeviceIdentity?,
    selfAddress: String,
    peerPublicKey: String,
    data: JSONObject,
    label: String?,
  ): String {
    if (data.optString("type").isBlank()) {
      Log.w(TAG, "El paquete saliente ${label ?: "sin etiqueta"} no declara tipo")
    }
    val signed = if (device != null) {
      device.crypto.buildLinkedControl(device.deviceId, identity.upeerID(), selfAddress, data.toString())
    } else {
      identity.buildSignedPacket(selfAddress, data.toString())
    }
    val ciphertext = identity.seal(signed.toByteArray(Charsets.UTF_8), peerPublicKey)
    return JSONObject().put("type", "SEALED").put("ciphertext", ciphertext).toString()
  }

  fun tryFrame(
    identity: CryptoIdentity,
    device: DeviceIdentity?,
    selfAddress: String,
    peerPublicKey: String,
    data: JSONObject,
    label: String,
  ): String? = runCatching { frame(identity, device, selfAddress, peerPublicKey, data, label) }.getOrNull()

  private const val TAG = "UPeerTransport"
}
