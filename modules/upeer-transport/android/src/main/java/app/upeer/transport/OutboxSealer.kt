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
    val nextPacket = seal(entry, target) ?: return Result.Failed("resultado de cifrado inválido")
    if (!link.sendPacket(activeBridge, target.address, nextPacket)) return Result.Failed("writeFrame falló")
    return Result.Sent
  }

  fun seal(entry: JSONObject, target: Target): String? {
    val messageID = entry.optString("id")
    val currentIdentity = identity.current() ?: return null
    val currentDevice = target.binding?.let { identity.currentDevice() }
      ?.takeIf { it.accountUpeerId == currentIdentity.upeerID() }
    val useDevice = target.binding != null && currentDevice != null
    return try {
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
      if (nextSession.isEmpty() || nextPacket.isEmpty()) return null
      // La sesión se guarda en el contexto del dispositivo del contacto, no en `direct`:
      // una sola sesión por cuenta se desincroniza en cuanto el otro lado usa más de un
      // dispositivo. El escritorio hace lo mismo.
      state.saveSession(target.upeerId, nextSession, DirectRatchetContext.forDevice(target.binding?.deviceId))
      outbox.setPacket(messageID, nextPacket)
      nextPacket
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo cifrar $messageID para ${target.upeerId}: ${message(cause)}")
      null
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
