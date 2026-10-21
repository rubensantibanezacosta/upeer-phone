package app.upeer.transport;

interface IYggdrasilTransport {
  String startWithPeers(String peersJSON, in byte[] seed);
  String start(in byte[] configJSON);
  String nodeInfo();
  String deriveIdentity(in byte[] seed);
  byte[] generateNodeSeed();
  void stop();
  void ensureForeground();
  String peerStatusJSON();
  String peerPoolJSON();
  long connectedPeerCount();
  void retryPeers();
  int dialTCP(String address, int port);
  int dialUDP(String address, int port);
  void write(int connectionID, in byte[] payload);
  void writeDatagram(int connectionID, in byte[] payload);
  void closeConnection(int connectionID);
  byte[] read(int connectionID, int maxBytes, int timeoutMillis);
}