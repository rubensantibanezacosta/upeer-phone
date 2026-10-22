package app.upeer.transport.dht

object DhtLimits {
  const val MAX_JSON_CHARS = 64 * 1024
  const val MAX_JSON_DEPTH = 16
  const val MAX_QUERY_ID_LENGTH = 64
  const val MAX_NODE_ID_LENGTH = 128
  const val MIN_UPEEER_ID_LENGTH = 32
  const val MAX_UPEEER_ID_LENGTH = 128
  const val MAX_ADDRESS_LENGTH = 100
  const val MAX_NODES = 20
  const val MAX_NEIGHBORS = 20
  const val MAX_PEERS = 50
  const val MAX_VALUE_CHARS = 10_000
  const val MAX_STORE_TTL_SECONDS = 2_592_000L
  const val MAX_POW_PROOF_LENGTH = 256
  const val MAX_RESPONSE_AGE_MILLIS = 30_000L
}
