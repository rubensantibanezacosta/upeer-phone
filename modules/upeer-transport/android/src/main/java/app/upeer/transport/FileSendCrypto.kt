package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity

internal data class SealedChunk(val data: String, val iv: String, val tag: String)

internal interface FileSendCrypto {
  fun newFileKey(): ByteArray
  fun sealFileKeyFor(fileKey: ByteArray, recipientPublicKeyHex: String): String
  fun encryptFileChunk(key: ByteArray, plaintext: ByteArray): SealedChunk?
}

internal class BridgeFileSendCrypto(private val crypto: CryptoIdentity) : FileSendCrypto {
  override fun newFileKey(): ByteArray = crypto.newFileKey()

  override fun sealFileKeyFor(fileKey: ByteArray, recipientPublicKeyHex: String): String =
    crypto.sealFileKeyFor(fileKey, recipientPublicKeyHex)

  override fun encryptFileChunk(key: ByteArray, plaintext: ByteArray): SealedChunk? {
    val sealed = crypto.encryptFileChunk(key, plaintext) ?: return null
    return SealedChunk(sealed.getData(), sealed.getIV(), sealed.getTag())
  }
}
