package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONObject

internal object OutboxPacketFactory {
  fun handshakeRequest(
    identity: CryptoIdentity,
    selfAddress: String,
    signedPreKeyJSON: () -> String,
    device: DeviceIdentity?,
    deviceSignedPreKeys: DeviceSignedPreKeyStore,
    deviceBindings: DeviceMessagingBindings,
    profile: JSONObject,
  ): String {
    val deviceFields = device?.let {
      deviceBindings.handshakeFields(it, deviceSignedPreKeys)
    }
    if (identity.isDeviceOnly()) {
      val linked = handshakeDevice(identity, device, deviceFields)
      return linked.crypto.buildLinkedHandshakeRequest(
        identity.publicKeyHex(),
        identity.upeerID(),
        selfAddress,
        profile.optString("alias"),
        profile.optString("avatar"),
        deviceFields?.toString().orEmpty(),
      )
    }
    return identity.buildHandshakeRequestWithProfile(
      selfAddress,
      signedPreKeyJSON(),
      profile.optString("alias"),
      profile.optString("avatar"),
      deviceFields?.toString().orEmpty(),
    )
  }

  fun handshakeAccept(
    identity: CryptoIdentity,
    selfAddress: String,
    signedPreKeyJSON: () -> String,
    device: DeviceIdentity?,
    deviceSignedPreKeys: DeviceSignedPreKeyStore,
    deviceBindings: DeviceMessagingBindings,
    profile: JSONObject,
  ): String {
    val deviceFields = device?.let {
      deviceBindings.handshakeFields(it, deviceSignedPreKeys)
    }
    if (identity.isDeviceOnly()) {
      val linked = handshakeDevice(identity, device, deviceFields)
      return linked.crypto.buildLinkedHandshakeAccept(
        identity.publicKeyHex(),
        identity.upeerID(),
        selfAddress,
        profile.optString("alias"),
        profile.optString("avatar"),
        deviceFields?.toString().orEmpty(),
      )
    }
    return identity.buildHandshakeAcceptWithProfile(
      selfAddress,
      signedPreKeyJSON(),
      profile.optString("alias"),
      profile.optString("avatar"),
      deviceFields?.toString().orEmpty(),
    )
  }

  private fun handshakeDevice(
    identity: CryptoIdentity,
    device: DeviceIdentity?,
    deviceFields: JSONObject?,
  ): DeviceIdentity {
    if (device == null || deviceFields == null) {
      throw IllegalStateException("El modo dispositivo necesita certificado y SPK de dispositivo para el handshake")
    }
    if (device.accountUpeerId != identity.upeerID()) {
      throw IllegalStateException("El certificado de dispositivo pertenece a otra cuenta")
    }
    if (!device.deviceId.equals(deviceFields.optString("deviceId"), ignoreCase = true)) {
      throw IllegalStateException("El certificado de dispositivo no coincide con la SPK preparada")
    }
    return device
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
    context: String = "direct",
  ): String = device.crypto.buildLinkedDrReset(
    identity.upeerID(),
    selfAddress,
    deviceSignedPreKeys.currentJSON(device),
    context,
    device.deviceId,
  )
}
