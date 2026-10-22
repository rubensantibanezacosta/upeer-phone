package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONObject

internal object OutboxPacketFactory {
  fun handshakeRequest(
    identity: CryptoIdentity,
    selfAddress: String,
    signedPreKeyJSON: String,
    device: DeviceIdentity?,
    deviceSignedPreKeys: DeviceSignedPreKeyStore,
    deviceBindings: DeviceMessagingBindings,
  ): String {
    val deviceFields = device?.let {
      deviceBindings.handshakeFields(it, deviceSignedPreKeys)
    }
    return if (deviceFields != null) {
      identity.buildHandshakeRequestWithDevice(selfAddress, signedPreKeyJSON, "", deviceFields.toString())
    } else {
      identity.buildHandshakeRequest(selfAddress, signedPreKeyJSON, "")
    }
  }

  fun chat(
    identity: CryptoIdentity,
    device: DeviceIdentity,
    binding: DeviceBinding,
    selfAddress: String,
    session: String,
    content: String,
    messageID: String,
    replyTo: String,
    timestamp: Long,
  ): JSONObject = JSONObject(
    device.crypto.buildLinkedChat(
      identity.upeerID(),
      selfAddress,
      binding.devicePublicKey,
      binding.signedPreKey.optString("spkPub"),
      session,
      content,
      messageID,
      replyTo,
      binding.signedPreKey.optLong("spkId"),
      timestamp,
      device.deviceId,
    ),
  )
}
