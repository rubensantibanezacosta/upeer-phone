package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class OutboxSealer(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val outbox: OutboxStore,
  private val link: OutboxLink,
  private val selfAddress: () -> String,
) {
  data class Target(
    val upeerId: String,
    val address: String,
    val publicKey: String,
    val session: String?,
    val signedPreKey: JSONObject?,
    val binding: DeviceBinding?,
  )

  sealed class Result {
    data object Sent : Result()
    data class Failed(val reason: String) : Result()
  }

  fun recipientSpk(target: Target): JSONObject? {
    val currentIdentity = identity.current() ?: return null
    val currentDevice = target.binding?.let { identity.currentDevice() }
      ?.takeIf { it.accountUpeerId == currentIdentity.upeerID() }
    return if (target.binding != null && currentDevice != null) target.binding.signedPreKey else target.signedPreKey
  }

  fun sealAndSend(activeBridge: Bridge, entry: JSONObject, target: Target): Result {
    val messageID = entry.optString("id")
    val currentIdentity = identity.current() ?: return Result.Failed("identidad no disponible")
    val currentDevice = target.binding?.let { identity.currentDevice() }
      ?.takeIf { it.accountUpeerId == currentIdentity.upeerID() }
    val useDevice = target.binding != null && currentDevice != null
    try {
      val result = OutboxPacketFactory.packet(
        currentIdentity,
        currentDevice.takeIf { useDevice },
        target.binding,
        selfAddress(),
        target.publicKey,
        target.session.orEmpty(),
        target.signedPreKey,
        entry,
      )
      val nextSession = result.optString("session")
      val nextPacket = result.optString("packet")
      if (nextSession.isEmpty() || nextPacket.isEmpty()) return Result.Failed("resultado de cifrado inválido")
      state.saveSession(target.upeerId, nextSession)
      outbox.setPacket(messageID, nextPacket)
      if (!link.sendPacket(activeBridge, target.address, nextPacket)) return Result.Failed("writeFrame falló")
      return Result.Sent
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo cifrar $messageID para ${target.upeerId}: ${message(cause)}")
      return Result.Failed(message(cause))
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
