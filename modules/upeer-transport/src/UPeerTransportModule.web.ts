import { NativeModule, registerWebModule } from 'expo';

const NATIVE_ONLY_ERROR = 'Esta función solo está disponible en la app Android.';

class UPeerTransportModule extends NativeModule<{}> {
  async generateNodeSeed(): Promise<number[]> { throw new Error(NATIVE_ONLY_ERROR); }
  async start(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async nodeInfo(): Promise<null> { return null; }
  async startWithPeers(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async stop(): Promise<void> { throw new Error(NATIVE_ONLY_ERROR); }
  async setForeground(): Promise<void> { throw new Error(NATIVE_ONLY_ERROR); }
  async peerStatus(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async peerPool(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async connectedPeerCount(): Promise<number> { return 0; }
  async retryPeers(): Promise<void> { throw new Error(NATIVE_ONLY_ERROR); }
  async dialTCP(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async dialUDP(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async writeTCP(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async writeUDP(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async read(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async closeConnection(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async listenTCP(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async acceptTCP(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async closeListener(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async readFrame(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async writeFrame(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async validateControlFrame(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async inboundStatus(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async setIdentity(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async clearIdentity(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async identityStatus(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async sendChat(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async editChat(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async messagesJSON(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async conversationsJSON(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async markConversationRead(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async sendTyping(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async sendReaction(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deleteMessage(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deleteConversation(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async setBlockedPeers(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async setKnownContacts(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async setNotificationPrefs(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async setMutedPeers(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async registerPeer(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async flushOutbox(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async protocolStatus(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async locationBlock(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deviceIdentityStatus(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async accountRegistry(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async accountRegistryStatus(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async revokeAccountDevice(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async validateDeviceAdmission(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async dhtLocationStatus(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async lookupContactLocation(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async verifyLocationBlock(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deviceEnrollmentBegin(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deviceEnrollmentRequestJSON(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deviceEnrollmentFinish(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deviceEnrollmentCancel(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async deviceEnrollmentStatusJSON(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async enrollmentAuthorizerCreateInvite(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async enrollmentAuthorizerAcceptRequest(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async enrollmentAuthorizerComplete(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async enrollmentAuthorizerStatusJSON(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async enrollmentAuthorizerCancel(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
  async enrollmentAuthorizerCancelAll(): Promise<never> { throw new Error(NATIVE_ONLY_ERROR); }
}

export default registerWebModule(UPeerTransportModule, 'UPeerTransport');
