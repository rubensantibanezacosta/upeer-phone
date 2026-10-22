package app.upeer.transport.crypto

object Blake2b {
  private const val BLOCK_BYTES = 128
  private const val ROUNDS = 12
  private const val DIGEST_BYTES = 32

  private val iv = longArrayOf(
    0x6a09e667f3bcc908L,
    -0x4498517a7b3558c5L,
    0x3c6ef372fe94f82bL,
    -0x5ab00ac5a0e2c90fL,
    0x510e527fade682d1L,
    -0x64fa9773d4c193e1L,
    0x1f83d9abfb41bd6bL,
    0x5be0cd19137e2179L,
  )

  private val sigma = arrayOf(
    intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
    intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
    intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
    intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
    intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
    intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
    intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
    intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
    intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
    intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
  )

  fun hash256(input: ByteArray): ByteArray {
    val state = iv.copyOf()
    state[0] = state[0] xor 0x01010000L xor DIGEST_BYTES.toLong()
    var offset = 0
    var counter = 0L
    while (input.size - offset > BLOCK_BYTES) {
      val block = ByteArray(BLOCK_BYTES)
      input.copyInto(block, 0, offset, offset + BLOCK_BYTES)
      counter += BLOCK_BYTES
      compress(state, block, counter, false)
      offset += BLOCK_BYTES
    }
    val finalBlock = ByteArray(BLOCK_BYTES)
    val remaining = input.size - offset
    input.copyInto(finalBlock, 0, offset, input.size)
    counter += remaining
    compress(state, finalBlock, counter, true)
    val digest = ByteArray(DIGEST_BYTES)
    for (wordIndex in 0 until DIGEST_BYTES / 8) {
      val word = state[wordIndex]
      for (byteIndex in 0 until 8) {
        digest[wordIndex * 8 + byteIndex] = (word ushr (byteIndex * 8)).toByte()
      }
    }
    return digest
  }

  private fun compress(state: LongArray, block: ByteArray, counter: Long, last: Boolean) {
    val message = LongArray(16) { index -> load64(block, index * 8) }
    val vector = LongArray(16) { index -> if (index < 8) state[index] else iv[index - 8] }
    vector[12] = vector[12] xor counter
    if (last) vector[14] = vector[14] xor -1L

    for (round in 0 until ROUNDS) {
      val schedule = sigma[round % sigma.size]
      mix(vector, 0, 4, 8, 12, message[schedule[0]], message[schedule[1]])
      mix(vector, 1, 5, 9, 13, message[schedule[2]], message[schedule[3]])
      mix(vector, 2, 6, 10, 14, message[schedule[4]], message[schedule[5]])
      mix(vector, 3, 7, 11, 15, message[schedule[6]], message[schedule[7]])
      mix(vector, 0, 5, 10, 15, message[schedule[8]], message[schedule[9]])
      mix(vector, 1, 6, 11, 12, message[schedule[10]], message[schedule[11]])
      mix(vector, 2, 7, 8, 13, message[schedule[12]], message[schedule[13]])
      mix(vector, 3, 4, 9, 14, message[schedule[14]], message[schedule[15]])
    }

    for (index in 0 until 8) {
      state[index] = state[index] xor vector[index] xor vector[index + 8]
    }
  }

  private fun mix(vector: LongArray, a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
    vector[a] = vector[a] + vector[b] + x
    vector[d] = rotateRight(vector[d] xor vector[a], 32)
    vector[c] = vector[c] + vector[d]
    vector[b] = rotateRight(vector[b] xor vector[c], 24)
    vector[a] = vector[a] + vector[b] + y
    vector[d] = rotateRight(vector[d] xor vector[a], 16)
    vector[c] = vector[c] + vector[d]
    vector[b] = rotateRight(vector[b] xor vector[c], 63)
  }

  private fun load64(block: ByteArray, offset: Int): Long {
    var value = 0L
    for (index in 0 until 8) {
      value = value or ((block[offset + index].toLong() and 0xffL) shl (index * 8))
    }
    return value
  }

  private fun rotateRight(value: Long, bits: Int): Long = (value ushr bits) or (value shl (64 - bits))
}
