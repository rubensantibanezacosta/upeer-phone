package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class ControlSender(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val bridgeProvider: () -> Bridge?,
) {
  fun sendTyping(upeerId: String) {
    send(upeerId, "upeer-typing", TypingIndicators.data(), "TYPING")
  }

  fun sendPong(upeerId: String) {
    send(upeerId, "upeer-pong", ControlPings.pong(), "PONG")
  }

  fun sendReaction(upeerId: String, messageID: String, emoji: String, remove: Boolean) {
    if (!ReplayPolicy.validMessageID(messageID) || !ChatReactions.validEmoji(emoji)) return
    val selfUpeerId = identity.current()?.upeerID() ?: return
    state.messages.reactLocally(messageID, upeerId, selfUpeerId, emoji, remove)
    send(
      upeerId,
      "upeer-reaction",
      ChatReactions.data(messageID, emoji, remove, System.currentTimeMillis()),
      "CHAT_REACTION",
    )
  }

  fun deleteMessage(upeerId: String, messageID: String) {
    if (!ReplayPolicy.validMessageID(messageID)) return
    state.messages.deleteLocally(messageID, upeerId)
    send(upeerId, "upeer-delete", ChatDeletes.data(messageID, System.currentTimeMillis()), "CHAT_DELETE")
  }

  fun sendReadReceipts(upeerId: String, messageIDs: List<String>) {
    val activeBridge = bridgeProvider() ?: return
    val address = state.addressFor(upeerId) ?: return
    val peerPublicKey = state.publicKeyFor(upeerId) ?: return
    val thread = Thread({
      val currentIdentity = identity.current() ?: return@Thread
      var connectionID = 0
      try {
        connectionID = activeBridge.dialTCP(address, PORT)
        var sent = 0
        for (messageID in messageIDs.take(ReadReceipts.MAX_PER_CONVERSATION)) {
          val frame = ReadReceipts.frame(
            currentIdentity,
            activeBridge.address(),
            peerPublicKey,
            messageID,
            System.currentTimeMillis(),
          )
          activeBridge.writeFrame(connectionID, frame.toByteArray(Charsets.UTF_8))
          sent += 1
        }
        Log.i(TAG, "READ enviado a $address para $sent mensajes")
      } catch (cause: Exception) {
        Log.w(TAG, "No se pudo enviar READ a $address: ${message(cause)}")
      } finally {
        if (connectionID != 0) {
          try {
            activeBridge.closeConnection(connectionID)
          } catch (cause: Exception) {
            Log.w(TAG, "No se pudo cerrar la conexión de READ: ${message(cause)}")
          }
        }
      }
    }, "upeer-read")
    thread.isDaemon = true
    thread.start()
  }

  fun scheduleAck(senderUpeerId: String, messageID: String) {
    val thread = Thread({ sendAck(senderUpeerId, messageID) }, "upeer-ack")
    thread.isDaemon = true
    thread.start()
  }

  private fun sendAck(senderUpeerId: String, messageID: String) {
    val activeBridge = bridgeProvider() ?: return
    val address = state.addressFor(senderUpeerId) ?: return
    val currentIdentity = identity.current() ?: return
    var connectionID = 0
    try {
      val ack = currentIdentity.buildAck(activeBridge.address(), messageID, "delivered")
      connectionID = activeBridge.dialTCP(address, PORT)
      activeBridge.writeFrame(connectionID, ack.toByteArray(Charsets.UTF_8))
      Log.i(TAG, "ACK enviado a $address para $messageID")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar ACK a $address: ${message(cause)}")
    } finally {
      if (connectionID != 0) {
        try {
          activeBridge.closeConnection(connectionID)
        } catch (cause: Exception) {
          Log.w(TAG, "No se pudo cerrar la conexión de ACK: ${message(cause)}")
        }
      }
    }
  }

  private fun send(upeerId: String, threadName: String, data: JSONObject, label: String) {
    val activeBridge = bridgeProvider() ?: return
    val address = state.addressFor(upeerId) ?: return
    val peerPublicKey = state.publicKeyFor(upeerId) ?: return
    val currentIdentity = identity.current() ?: return
    val thread = Thread({
      var connectionID = 0
      try {
        val frame = SealedControl.frame(currentIdentity, activeBridge.address(), peerPublicKey, data)
        connectionID = activeBridge.dialTCP(address, PORT)
        activeBridge.writeFrame(connectionID, frame.toByteArray(Charsets.UTF_8))
      } catch (cause: Exception) {
        Log.w(TAG, "No se pudo enviar $label a $address: ${message(cause)}")
      } finally {
        if (connectionID != 0) {
          try {
            activeBridge.closeConnection(connectionID)
          } catch (cause: Exception) {
            Log.w(TAG, "No se pudo cerrar la conexión de $label: ${message(cause)}")
          }
        }
      }
    }, threadName)
    thread.isDaemon = true
    thread.start()
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val PORT = 50005L
  }
}
