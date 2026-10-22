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

  fun packet(
    identity: CryptoIdentity,
    device: DeviceIdentity?,
    binding: DeviceBinding?,
    selfAddress: String,
    peerPublicKey: String,
    session: String,
    signedPreKey: JSONObject?,
    entry: JSONObject,
  ): JSONObject {
    val isUpdate = entry.optString("kind", "CHAT") == "CHAT_UPDATE"
    val messageID = if (isUpdate) entry.optString("msgId") else entry.optString("id")
    if (device != null && binding != null) {
      return if (isUpdate) {
        JSONObject(
          device.crypto.buildLinkedChatUpdate(
            identity.upeerID(),
            selfAddress,
            binding.devicePublicKey,
            binding.signedPreKey.optString("spkPub"),
            session,
            entry.optString("content"),
            messageID,
            entry.optInt("version").toLong(),
            binding.signedPreKey.optLong("spkId"),
            entry.optLong("timestamp"),
            device.deviceId,
          ),
        )
      } else {
        JSONObject(
          device.crypto.buildLinkedChat(
            identity.upeerID(),
            selfAddress,
            binding.devicePublicKey,
            binding.signedPreKey.optString("spkPub"),
            session,
            entry.optString("content"),
            messageID,
            entry.optString("replyTo"),
            binding.signedPreKey.optLong("spkId"),
            entry.optLong("timestamp"),
            device.deviceId,
          ),
        )
      }
    }
    val packet = if (isUpdate) {
      identity.buildChatUpdate(
        selfAddress,
        peerPublicKey,
        signedPreKey?.optString("spkPub").orEmpty(),
        session,
        entry.optString("content"),
        messageID,
        entry.optInt("version").toLong(),
        signedPreKey?.optLong("spkId") ?: 0L,
        entry.optLong("timestamp"),
      )
    } else {
      identity.buildChat(
        selfAddress,
        peerPublicKey,
        signedPreKey?.optString("spkPub").orEmpty(),
        session,
        entry.optString("content"),
        messageID,
        entry.optString("replyTo"),
        signedPreKey?.optLong("spkId") ?: 0L,
        entry.optLong("timestamp"),
      )
    }
    return JSONObject(packet)
  }

  fun drReset(
    identity: CryptoIdentity,
    device: DeviceIdentity,
    selfAddress: String,
    deviceSignedPreKeys: DeviceSignedPreKeyStore,
  ): String = device.crypto.buildLinkedDrReset(
    identity.upeerID(),
    selfAddress,
    deviceSignedPreKeys.currentJSON(device),
    "direct",
    device.deviceId,
  )
}
