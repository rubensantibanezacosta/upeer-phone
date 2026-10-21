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
  int listenTCP(int port);
  int acceptTCP(int listenerID, int timeoutMillis);
  void closeListener(int listenerID);
  byte[] readFrame(int connectionID, int timeoutMillis);
  void writeFrame(int connectionID, in byte[] payload);
  String validateControlFrame(in byte[] payload);
  String inboundStatusJSON();
  void setIdentity(in byte[] secretKey);
  void clearIdentity();
  String identityStatusJSON();
  String sendChat(String upeerId, String content, String replyTo);
  String messagesJSON(String upeerId, int limit);
  String conversationsJSON();
  void markConversationRead(String upeerId);
  boolean registerPeer(String upeerId, String address, String publicKey, String signedPreKeyJSON);
  void flushOutbox();
  String protocolStatusJSON();
}