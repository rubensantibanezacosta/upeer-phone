package app.upeer.transport.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Blake2bTest {
  @Test
  fun hashesKnownVectors() {
    assertEquals(
      "0e5751c026e543b2e8ab2eb06099daa1d1e5df47778f7787faab45cdf12fe3a8",
      hex(Blake2b.hash256(ByteArray(0))),
    )
    assertEquals(
      "bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319",
      hex(Blake2b.hash256("abc".toByteArray(Charsets.UTF_8))),
    )
  }

  @Test
  fun hashesEd25519PublicKeyToDesktopUpeerId() {
    val publicKey = ByteArray(32) { index -> index.toByte() }
    assertEquals(
      "cb2f5160fc1f7e05a55ef49d340b48da2e5a78099d53393351cd579dd42503d6",
      hex(Blake2b.hash256(publicKey)),
    )
  }

  @Test
  fun handlesBlockBoundaryLengths() {
    val block = ByteArray(128) { index -> (index * 7).toByte() }
    val first = Blake2b.hash256(block)
    val second = Blake2b.hash256(block.copyOf())
    assertArrayEquals(first, second)
    assertEquals(32, first.size)
  }

  private fun hex(bytes: ByteArray): String =
    bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
