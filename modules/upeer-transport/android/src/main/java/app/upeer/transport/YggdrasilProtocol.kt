package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.ContactEncryptionAdmission
import app.upeer.transport.dht.DhtLocation
import app.upeer.transport.vault.VaultDirectory
import app.upeer.transport.vault.VaultMailboxStore
import app.upeer.transportbridge.transportbridge.Bridge
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal class YggdrasilProtocol(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val outboxStore: OutboxStore,
  private val onInboundMessage: (String, String, String) -> Unit,
  private val admission: DeviceAdmissionGuard,
  private val contactAdmission: ContactEncryptionAdmission,
  private val vaultMailbox: VaultMailboxStore,
  private val rateLimiter: InboundRateLimiter = InboundRateLimiter(),
  private val enrollmentResponse: (String) -> Boolean = { false },
  private val enrollmentRequest: (String, String) -> String = { _, _ -> "" },
  private val transferStorage: () -> java.io.File = { java.io.File(System.getProperty("java.io.tmpdir") ?: ".") },
  private val onGroupChanged: (String) -> Unit = {},
  ) {
  private val policy = InboundPolicy(rateLimiter)
  private val egress = ProtocolEgress(::selfAddress)
  private val transfers = FileTransferState(transferStorage)
  private val handshake = YggdrasilHandshake(identity, signedPreKeys, deviceSignedPreKeys, deviceBindings, state)
  private val outbox = YggdrasilOutbox(
    identity,
    signedPreKeys,
    deviceSignedPreKeys,
    deviceBindings,
    state,
    outboxStore,
    admission,
    contactAdmission,
    vaultMailbox,
  )
  private val chatDecryptor = YggdrasilChatDecryptor(
    identity,
    signedPreKeys,
    deviceSignedPreKeys,
    state,
    { upeerId, context -> outbox.sendDrReset(upeerId, context) },
  )
  @Volatile
  private var bridge: Bridge? = null

  private val controlSender = ControlSender(identity, state) { bridge }
  private val controlReplies = ControlReplies(identity, deviceBindings, state, outbox, rateLimiter, controlSender::sendPong)
  private val chatInbound = ChatInbound(state, chatDecryptor, controlSender, onInboundMessage)
  private val groupSealer = GroupControlSealer(identity, state)
  private val groupAdmin = GroupAdmin(identity, state, egress, groupSealer, { bridge }, onGroupChanged)
  private val groupInbound = GroupInbound(
    state = state,
    decryptor = chatDecryptor,
    controlSender = controlSender,
    onInboundMessage = onInboundMessage,
    onGroupChanged = onGroupChanged,
    onMemberLeft = groupAdmin::rotateAfterMemberLeft,
    myUpeerId = { identity.current()?.upeerID().orEmpty() },
  )
  private val groupOutbound = GroupOutbound(identity, state, deviceBindings, egress) { bridge }
  private val fileInbound = FileInbound(
    accountCrypto = { identity.current()?.let { BridgeFileReceiveCrypto(it) } },
    deviceCrypto = { identity.currentDevice()?.crypto?.let { BridgeFileReceiveCrypto(it) } },
    transfers = transfers,
    messages = StateTransportMessages(state),
    send = { upeerId, data, label ->
      val active = bridge ?: return@FileInbound false
      val address = state.addressFor(upeerId) ?: return@FileInbound false
      val peerPublicKey = state.publicKeyFor(upeerId) ?: return@FileInbound false
      val current = identity.current() ?: return@FileInbound false
      val frame = SealedControl.frame(current, identity.currentDevice(), active.address(), peerPublicKey, data)
      egress.send(active, address, frame, label)
    },
    groupMembers = ::activeGroupMembers,
  )
  private val fileOutbound = FileOutbound(
    accountCrypto = { identity.current()?.let { BridgeFileSendCrypto(it) } },
    deviceCrypto = { identity.currentDevice()?.crypto?.let { BridgeFileSendCrypto(it) } },
    directory = StateFileSendDirectory(state),
    messages = StateTransportMessages(state),
    send = { upeerId, data, label ->
      val active = bridge ?: return@FileOutbound false
      val address = state.addressFor(upeerId) ?: return@FileOutbound false
      val peerPublicKey = state.publicKeyFor(upeerId) ?: return@FileOutbound false
      val current = identity.current() ?: return@FileOutbound false
      val frame = SealedControl.frame(current, identity.currentDevice(), active.address(), peerPublicKey, data)
      egress.send(active, address, frame, label)
    },
    groupMembers = ::activeGroupMembers,
    selfUpeerId = { identity.current()?.upeerID().orEmpty() },
  )
  private val vaultReplies = VaultReplies(
    identity,
    deviceBindings,
    state,
    vaultMailbox,
    policy,
    { active, address, json -> egress.send(active, address, json, "de vault") },
    ::replayVaultEntry,
  )
  private val sync = ProtocolSync(
    identity,
    deviceSignedPreKeys,
    deviceBindings,
    state,
    policy,
    ::selfAddress,
    ::validateLinked,
    { active, address, json -> egress.send(active, address, json, "de sincronización") },
    { admission.isAdmitted() },
  )

  fun attachVaultDirectory(directory: VaultDirectory) = outbox.attachVaultDirectory(directory)

  fun attachSyncDiscovery(
    store: (String, String) -> Int,
    read: (String) -> Any?,
    registry: () -> String,
    directPeer: () -> String = { "" },
  ) = sync.attachDiscovery(store, read, registry, directPeer)

  fun start(activeBridge: Bridge) {
    bridge = activeBridge
    outbox.start(activeBridge)
    outbox.queryVaults(activeBridge)
    outbox.renewVaultEntries(activeBridge)
    sync.start(activeBridge)
  }

  fun stop() {
    bridge = null
    outbox.stop()
  }

  fun clear() {
    outbox.stop()
    handshake.clear()
    outboxStore.clear()
    controlReplies.clear()
    contactAdmission.clear()
    state.clear()
  }

  fun createGroup(name: String, memberIdsJSON: String, avatar: String): String =
    groupAdmin.create(name, parseMembers(memberIdsJSON), avatar.ifEmpty { null })

  fun inviteToGroup(groupId: String, upeerId: String) = groupAdmin.invite(groupId, upeerId)

  fun removeGroupMember(groupId: String, upeerId: String) = groupAdmin.removeMember(groupId, upeerId)

  fun transferGroupAdmin(groupId: String, upeerId: String) = groupAdmin.transferAdmin(groupId, upeerId)

  fun leaveGroup(groupId: String) = groupAdmin.leave(groupId)

  fun renameGroup(groupId: String, name: String) = groupAdmin.rename(groupId, name)

  private fun parseMembers(memberIdsJSON: String): List<String> {
    val array = runCatching { org.json.JSONArray(memberIdsJSON) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
      array.optString(index).takeIf(String::isNotEmpty)
    }
  }

  fun sendChat(upeerId: String, content: String, replyTo: String): String =
    if (upeerId.startsWith(ProtocolGroups.PREFIX)) {
      groupOutbound.sendMessage(upeerId, content, replyTo)
    } else {
      outbox.enqueue(upeerId, content, replyTo)
    }

  fun editChat(upeerId: String, messageId: String, content: String): Int = outbox.editChat(upeerId, messageId, content)

  fun retryChat(upeerId: String, messageId: String): Boolean = outbox.retryChat(upeerId, messageId)

  fun markUserActive() = controlSender.markActive(System.currentTimeMillis())

  fun registerPeer(upeerId: String, address: String, publicKey: String, signedPreKeyJSON: String): Boolean {
    val accepted = state.upsertPeer(upeerId, address, publicKey, signedPreKeyJSON.ifBlank { null })
    if (accepted) {
      outbox.onHandshakeAccepted(upeerId)
      bridge?.let { outbox.queryVault(it, upeerId) }
    }
    return accepted
  }

  fun flushOutbox() = outbox.flush()
  fun peerPublicKey(upeerId: String): String? = state.publicKeyFor(upeerId)
  fun sendTyping(upeerId: String, state: String) = controlSender.sendTyping(upeerId, state)

  fun sendReaction(upeerId: String, messageID: String, emoji: String, remove: Boolean) =
    controlSender.sendReaction(upeerId, messageID, emoji, remove)

  fun deleteMessage(upeerId: String, messageID: String) =
    controlSender.deleteMessage(upeerId, messageID)

  fun markConversationRead(upeerId: String) {
    val transitioned = ReadReceipts.allowed(state.markConversationRead(upeerId), state.readReceiptsEnabled())
    if (transitioned.isNotEmpty()) controlSender.sendReadReceipts(upeerId, transitioned)
  }

  fun setReadReceipts(enabled: Boolean) = state.setReadReceipts(enabled)

  fun setProfile(alias: String, avatar: String) {
    val previous = state.profile()
    state.setProfile(alias, avatar)
    if (previous.toString() != state.profile().toString()) outbox.announceProfile()
  }

  fun deleteConversation(upeerId: String): Boolean = state.deleteConversation(upeerId)

  fun setBlockedPeers(upeerIds: List<String>) = state.setBlockedPeers(upeerIds)

  fun messagesJSON(upeerId: String, limit: Int): String = state.messagesJSON(upeerId, limit)

  fun searchMessagesJSON(query: String, limit: Int, offset: Int): String = state.searchMessagesJSON(query, limit, offset)

  fun searchChatMessagesJSON(upeerId: String, query: String, limit: Int, offset: Int): String =
    state.searchChatMessagesJSON(upeerId, query, limit, offset)

  fun historyBeforeJSON(upeerId: String, beforeTimestamp: Long, limit: Int): String =
    state.historyBeforeJSON(upeerId, beforeTimestamp, limit)

  fun conversationsJSON(): String = state.conversationsJSON()
  fun groupsJSON(): String = state.groupsJSON()

  fun peerSecurityJSON(upeerId: String): String = PeerSecurityReport.json(
    upeerId,
    state.peerFor(upeerId),
    identity.current()?.publicKeyHex().orEmpty(),
    state.sessionFor(upeerId) != null,
  )

  fun handleFrame(activeBridge: Bridge, payload: ByteArray, sourceAddress: String): String {
    val outer = JSONObject(String(payload, Charsets.UTF_8))
    val outerType = outer.optString("type")
    if (outerType == EnrollmentChannel.RESPONSE) return handleEnrollmentResponse(outer, sourceAddress)
    if (outerType == EnrollmentChannel.REQUEST) return handleEnrollmentRequest(activeBridge, outer, sourceAddress)
    if (outerType != "SEALED") return handleSignedPacket(activeBridge, payload, sourceAddress)
    if (!rateLimiter.allow("ip:$sourceAddress", outerType)) {
      Log.w(TAG, "SEALED limitado por origen $sourceAddress")
      return InboundPolicy.rateLimitedMetadata(outerType)
    }
    val currentIdentity = identity.current() ?: throw IllegalStateException("uPeer identity is not available")
    val ciphertext = outer.optString("ciphertext")
    var accountFailure: Throwable? = null
    val accountInner = try {
      currentIdentity.openSealed(ciphertext)
    } catch (cause: Exception) {
      accountFailure = cause
      null
    }
    val inner = accountInner ?: identity.currentDevice()?.let { device ->
      try {
        device.crypto.openSealed(ciphertext)
      } catch (cause: Exception) {
        Log.w(TAG, "El paquete SEALED no se pudo abrir con la clave de dispositivo: ${message(cause)}")
        null
      }
    } ?: throw IllegalStateException(
      "SEALED packet could not be opened with identity or device key",
      accountFailure,
    )
    return handleSignedPacket(activeBridge, inner, sourceAddress)
  }

  fun statusJSON(): String {
    val result = JSONObject(state.statusJSON())
    result
      .put("identityReady", identity.current() != null)
      .put("outbox", outboxStore.count())
      .put("rateLimited", rateLimiter.limitedCount())
    return result.toString()
  }

  fun syncStatusJSON(): String = state.syncStatusJSON()

  private fun handleSignedPacket(activeBridge: Bridge, payload: ByteArray, sourceAddress: String): String {
    val packet = JSONObject(String(payload, Charsets.UTF_8))
    val type = packet.optString("type")
    if (sync.isSyncType(type)) return sync.handle(activeBridge, payload, packet, type, sourceAddress)
    val declaredSender = packet.optString("senderUpeerId").lowercase()
    if (declaredSender.isNotEmpty() && state.isBlocked(declaredSender)) {
      Log.i(TAG, "Paquete $type descartado: $declaredSender está bloqueado")
      return InboundPolicy.blocked(packet, declaredSender)
    }
    if (declaredSender.isNotEmpty()) outbox.announceProfileTo(declaredSender)
    if (type == "HANDSHAKE_REQ" || type == "HANDSHAKE_ACCEPT") {
      val metadataJSON = activeBridge.validateControlFrame(payload)
      val metadata = JSONObject(metadataJSON)
      packet.optJSONObject("signedPreKey")?.let { metadata.put("signedPreKey", it) }
      packet.optString("alias").takeIf(String::isNotEmpty)?.let { metadata.put("alias", it) }
      packet.optString("avatar").takeIf(String::isNotEmpty)?.let { metadata.put("avatar", it) }
      val accountUpeerId = metadata.optString("senderUpeerId")
      val deviceAttached = deviceBindings.attach(metadata, packet, accountUpeerId)
      if (deviceBindings.hasFields(packet) && !deviceAttached) {
        Log.w(TAG, "Handshake $type de $accountUpeerId descartado: identidad de dispositivo inválida o revocada")
        return InboundPolicy.rejected(packet)
      }
      if (!policy.allow(metadata, sourceAddress)) return policy.rateLimited(metadata)
      if (state.rememberPeer(metadata)) outbox.onPeerReset(metadata.optString("senderUpeerId"))
      Log.i(
        TAG,
        "Handshake $type de ${metadata.optString("senderUpeerId")} " +
          "spk=${packet.optJSONObject("signedPreKey")?.optLong("spkId") ?: 0} " +
          "peer=${state.peerFor(metadata.optString("senderUpeerId"))?.optJSONObject("signedPreKey") != null}",
      )
      if (type == "HANDSHAKE_REQ") {
        val responder = Thread({ handshake.respond(activeBridge, metadataJSON) }, "upeer-handshake")
        responder.isDaemon = true
        responder.start()
      } else {
        outbox.onHandshakeAccepted(metadata.optString("senderUpeerId"))
      }
      return metadataJSON
    }
    if (type == "ACK" || type == "DR_RESET" || type == "READ" || type == "TYPING" || type == "PING" ||
      type == "CHAT_REACTION" || type == "CHAT_DELETE"
    ) {
      return controlReplies.handle(activeBridge, payload, packet, type, sourceAddress)
    }
    if (VAULT_TYPES.contains(type)) {
      return vaultReplies.handle(activeBridge, payload, packet, type, sourceAddress)
    }
    if (FILE_TYPES.contains(type)) {
      return handleFilePacket(activeBridge, payload, packet, type, sourceAddress)
    }
    if (type !in SIGNED_MESSAGE_TYPES) throw IllegalArgumentException("unsupported packet type $type")
    val senderUpeerId = packet.optString("senderUpeerId").lowercase()
    val publicKey = state.publicKeyFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    val senderDeviceId = packet.optString("senderDeviceId").lowercase()
    val isDevicePacket = senderDeviceId.isNotEmpty()
    val senderIdentityKey: String
    val metadataJSON: String
    if (isDevicePacket) {
      if (!DhtLocation.isHex(senderDeviceId, 32)) throw IllegalArgumentException("$type senderDeviceId is invalid")
      if (deviceBindings.isRevoked(senderUpeerId, senderDeviceId)) {
        Log.i(TAG, "Paquete $type descartado: el dispositivo $senderDeviceId está revocado")
        return InboundPolicy.rejected(packet)
      }
      val peer = state.peerFor(senderUpeerId) ?: throw IllegalStateException("unknown peer $senderUpeerId")
      if (!peer.optString("deviceId").equals(senderDeviceId, ignoreCase = true)) {
        throw IllegalArgumentException("$type announced an unknown sender device")
      }
      senderIdentityKey = peer.optString("devicePublicKey")
      if (!DhtLocation.isHex(senderIdentityKey, 32)) throw IllegalArgumentException("peer device key is unavailable")
      metadataJSON = activeBridge.validateLinkedSignedPacket(payload, senderIdentityKey, senderUpeerId)
    } else {
      senderIdentityKey = publicKey
      metadataJSON = activeBridge.validateSignedPacket(payload, publicKey)
    }
    val metadata = JSONObject(metadataJSON)
    if (!policy.allow(metadata, sourceAddress)) return policy.rateLimited(metadata)
    if (!isDevicePacket) state.rememberPeer(metadata)
    return when (type) {
      "CHAT_UPDATE" -> chatInbound.receiveUpdate(packet, senderUpeerId, senderIdentityKey, isDevicePacket, metadataJSON)
      "GROUP_INVITE" -> groupInbound.receiveInvite(packet, senderUpeerId, senderIdentityKey, isDevicePacket, metadataJSON)
      "GROUP_UPDATE" -> groupInbound.receiveUpdate(packet, senderUpeerId, senderIdentityKey, isDevicePacket, metadataJSON)
      "GROUP_MSG" -> groupInbound.receiveMessage(packet, senderUpeerId, metadataJSON)
      "GROUP_LEAVE" -> groupInbound.receiveLeave(packet, senderUpeerId, metadataJSON)
      "GROUP_ACK" -> receiveGroupAck(packet, metadataJSON)
      else -> chatInbound.receive(packet, senderUpeerId, senderIdentityKey, isDevicePacket, metadataJSON)
    }
  }

  private fun receiveGroupAck(packet: JSONObject, metadataJSON: String): String {
    val messageID = packet.optString("id")
    val groupId = packet.optString("groupId")
    if (!ReplayPolicy.validMessageID(messageID) || !ProtocolGroups.validId(groupId)) {
      throw IllegalArgumentException("GROUP_ACK id or groupId is invalid")
    }
    if (state.messages.groupAck(messageID, groupId, "delivered")) {
      Log.i(TAG, "GROUP_ACK aplicado a $messageID en $groupId")
    }
    return metadataJSON
  }

  private fun handleEnrollmentResponse(packet: JSONObject, sourceAddress: String): String {
    if (!rateLimiter.allow("ip:$sourceAddress", EnrollmentChannel.RESPONSE)) {
      Log.w(TAG, "ENROLL_RES limitado por origen $sourceAddress")
      return InboundPolicy.rejected(packet)
    }
    val sealed = EnrollmentChannel.sealedFrom(packet)
    if (sealed == null) {
      Log.w(TAG, "ENROLL_RES rechazado: la cuenta no pudo autorizar el dispositivo")
      return InboundPolicy.rejected(packet)
    }
    val installed = runCatching { enrollmentResponse(sealed) }.getOrElse { cause ->
      Log.w(TAG, "ENROLL_RES no se pudo instalar: ${cause.message}")
      false
    }
    if (!installed) return InboundPolicy.rejected(packet)
    Log.i(TAG, "Certificado de dispositivo instalado desde $sourceAddress")
    return JSONObject().put("type", EnrollmentChannel.RESPONSE).put("installed", true).toString()
  }

  private fun handleEnrollmentRequest(activeBridge: Bridge, packet: JSONObject, sourceAddress: String): String {
    if (!rateLimiter.allow("ip:$sourceAddress", EnrollmentChannel.REQUEST)) {
      Log.w(TAG, "ENROLL_REQ limitado por origen $sourceAddress")
      return InboundPolicy.rejected(packet)
    }
    val target = EnrollmentChannel.dialableAddress(packet.optString("senderAddress"))
    val sealed = EnrollmentChannel.requestSealed(packet)
    if (target == null || sealed == null) {
      Log.w(TAG, "ENROLL_REQ rechazado: sin sobre sellado o sin dirección de vuelta")
      return InboundPolicy.rejected(packet)
    }
    val inviteId = runCatching { JSONObject(sealed).optString("inviteId").lowercase() }.getOrDefault("")
    val response = runCatching { enrollmentRequest(inviteId, sealed) }.getOrElse { cause ->
      Log.w(TAG, "ENROLL_REQ no se pudo autorizar: ${message(cause)}")
      ""
    }
    if (!EnrollmentChannel.send(activeBridge, target, EnrollmentChannel.responseFrame(inviteId, response.ifEmpty { null }))) {
      Log.w(TAG, "ENROLL_RES no se pudo entregar a $target")
      return InboundPolicy.rejected(packet)
    }
    if (response.isEmpty()) return InboundPolicy.rejected(packet)
    Log.i(TAG, "Certificado de dispositivo emitido para $target")
    return JSONObject().put("type", EnrollmentChannel.REQUEST).put("handled", true).toString()
  }

  private fun selfAddress(): String = bridge?.address().orEmpty()

  private fun validateLinked(payload: ByteArray, publicKey: String, sender: String) {
    val active = bridge ?: throw IllegalStateException("no hay puente activo para validar la firma")
    active.validateLinkedSignedPacket(payload, publicKey, sender)
  }

  private fun replayVaultEntry(activeBridge: Bridge, bytes: ByteArray, sourceAddress: String) {
    handleFrame(activeBridge, bytes, sourceAddress)
  }

  private fun handleFilePacket(
    activeBridge: Bridge,
    payload: ByteArray,
    packet: JSONObject,
    type: String,
    sourceAddress: String,
  ): String {
    val senderUpeerId = packet.optString("senderUpeerId").lowercase()
    if (state.isBlocked(senderUpeerId)) {
      Log.i(TAG, "Paquete $type descartado: $senderUpeerId está bloqueado")
      return InboundPolicy.blocked(packet, senderUpeerId)
    }
    val publicKey = state.publicKeyFor(senderUpeerId) ?: run {
      Log.w(TAG, "Paquete $type de un contacto desconocido $senderUpeerId")
      return InboundPolicy.rejected(packet)
    }
    when (val signature = PacketSignature.of(packet)) {
      is PacketSignature.Linked -> {
        if (deviceBindings.isRevoked(senderUpeerId, signature.deviceId)) {
          Log.i(TAG, "Paquete $type descartado: el dispositivo ${signature.deviceId} está revocado")
          return InboundPolicy.rejected(packet)
        }
        val peer = state.peerFor(senderUpeerId) ?: return InboundPolicy.rejected(packet)
        if (!peer.optString("deviceId").equals(signature.deviceId, ignoreCase = true)) {
          Log.w(TAG, "Paquete $type anuncia un dispositivo desconocido ${signature.deviceId}")
          return InboundPolicy.rejected(packet)
        }
        val deviceKey = peer.optString("devicePublicKey")
        if (!DhtLocation.isHex(deviceKey, 32)) return InboundPolicy.rejected(packet)
        activeBridge.validateLinkedSignedPacket(payload, deviceKey, senderUpeerId)
      }
      PacketSignature.Account -> activeBridge.validateSignedPacket(payload, publicKey)
    }
    if (!rateLimiter.allow("ip:$sourceAddress", type) || !rateLimiter.allow("id:$senderUpeerId", type)) {
      Log.w(TAG, "Límite de recepción alcanzado para $type de $senderUpeerId")
      return InboundPolicy.rateLimitedMetadata(type)
    }
    if (fileOutbound.handle(packet, senderUpeerId, type)) return packet.toString()
    return fileInbound.handle(packet, senderUpeerId, type)
  }

  fun sendFile(
    upeerId: String,
    path: String,
    fileName: String,
    mimeType: String,
    caption: String?,
    isVoiceNote: Boolean,
    chatUpeerId: String?,
    messageId: String?,
  ): String {
    if (bridge == null) throw IllegalStateException("El transporte no está iniciado")
    if (!upeerId.startsWith(ProtocolGroups.PREFIX) && state.publicKeyFor(upeerId) == null) {
      throw IllegalStateException("El contacto $upeerId no es conocido")
    }
    return fileOutbound.start(
      FileOutbound.Request(upeerId, path, fileName, mimeType, caption, isVoiceNote, chatUpeerId, messageId),
    )
  }

  fun cancelFile(fileId: String): Boolean = fileOutbound.cancel(fileId)

  private fun activeGroupMembers(groupId: String): List<String>? {
    val group = state.groups.get(groupId) ?: return null
    if (group.optString("status") == "removed") return null
    val raw = group.optJSONArray("members") ?: return null
    return (0 until raw.length()).mapNotNull { index ->
      raw.optString(index).lowercase().takeIf(String::isNotEmpty)
    }
  }

  fun outboundFiles(): String = fileOutbound.sendingJSON()

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private val VAULT_TYPES = setOf("VAULT_STORE", "VAULT_QUERY", "VAULT_ACK", "VAULT_DELIVERY", "VAULT_RENEW")
    private val SIGNED_MESSAGE_TYPES = setOf(
      "CHAT",
      "CHAT_UPDATE",
      "GROUP_INVITE",
      "GROUP_UPDATE",
      "GROUP_MSG",
      "GROUP_ACK",
      "GROUP_LEAVE",
    )
    private val FILE_TYPES = setOf(
      "FILE_PROPOSAL",
      "FILE_ACCEPT",
      "FILE_CHUNK",
      "FILE_ACK",
      "FILE_DONE",
      "FILE_DONE_ACK",
      "FILE_CANCEL",
    )
  }
}
