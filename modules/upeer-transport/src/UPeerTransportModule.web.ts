import { NativeModule, registerWebModule } from 'expo';

class UPeerTransportModule extends NativeModule<{}> {
  async generateNodeSeed(): Promise<number[]> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async start(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async nodeInfo(): Promise<null> { return null; }
  async startWithPeers(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async stop(): Promise<void> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async setForeground(): Promise<void> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async peerStatus(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async peerPool(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async connectedPeerCount(): Promise<number> { return 0; }
  async retryPeers(): Promise<void> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async dialTCP(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async dialUDP(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async writeTCP(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async writeUDP(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async read(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
  async closeConnection(): Promise<never> { throw new Error('Yggdrasil requiere el módulo nativo Android.'); }
}

export default registerWebModule(UPeerTransportModule, 'UPeerTransport');
