package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity

internal interface FileReceiveCrypto {
  fun openFileKey(sealedHex: String): ByteArray?
  fun decryptFileChunk(key: ByteArray, ivHex: String, tagHex: String, dataBase64: String): ByteArray
}

internal class BridgeFileReceiveCrypto(private val crypto: CryptoIdentity) : FileReceiveCrypto {
  override fun openFileKey(sealedHex: String): ByteArray? = crypto.openFileKey(sealedHex)

  override fun decryptFileChunk(key: ByteArray, ivHex: String, tagHex: String, dataBase64: String): ByteArray =
    crypto.decryptFileChunk(key, ivHex, tagHex, dataBase64)
}
