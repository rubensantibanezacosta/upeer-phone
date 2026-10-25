package app.upeer.transport.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class VaultPointerKeyTest {
  private val owner = "aa".repeat(32)
  private val device = "bb".repeat(32)
  private val now = 1_700_000_000_000L

  private fun dayString(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

  @Test
  fun `derives a twenty byte hexadecimal key`() {
    val key = VaultPointerKey.key(owner, null, now)

    assertEquals(40, key.length)
    assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
  }

  @Test
  fun `matches the desktop derivation`() {
    val expected = java.security.MessageDigest.getInstance("SHA-256")
      .digest("vault-ptr-v2:$owner:${dayString(now)}".toByteArray(Charsets.UTF_8))
      .copyOf(20)
      .joinToString("") { "%02x".format(it) }

    assertEquals(expected, VaultPointerKey.key(owner, null, now))
  }

  @Test
  fun `scopes the key by device`() {
    val shared = VaultPointerKey.key(owner, null, now)
    val scoped = VaultPointerKey.key(owner, device, now)

    assertNotEquals(shared, scoped)
    val expected = java.security.MessageDigest.getInstance("SHA-256")
      .digest("vault-ptr-v2:$owner:device:$device:${dayString(now)}".toByteArray(Charsets.UTF_8))
      .copyOf(20)
      .joinToString("") { "%02x".format(it) }
    assertEquals(expected, scoped)
  }

  @Test
  fun `normalizes case`() {
    assertEquals(VaultPointerKey.key(owner, null, now), VaultPointerKey.key(owner.uppercase(), null, now))
    assertEquals(VaultPointerKey.key(owner, device, now), VaultPointerKey.key(owner, device.uppercase(), now))
  }

  @Test
  fun `salted key rotates every utc day`() {
    val nextDay = now + 24L * 60L * 60L * 1000L

    assertNotEquals(VaultPointerKey.key(owner, null, now), VaultPointerKey.key(owner, null, nextDay))
  }

  @Test
  fun `read keys include today and yesterday`() {
    val today = VaultPointerKey.key(owner, null, now)
    val yesterday = VaultPointerKey.key(owner, null, now - 24L * 60L * 60L * 1000L)

    assertEquals(listOf(today, yesterday), VaultPointerKey.readKeys(owner, null, now))
  }

  @Test
  fun `read keys are unique and always include the shared scope`() {
    val keys = VaultPointerKey.readKeys(owner, device, now)

    assertEquals(keys.distinct(), keys)
    assertEquals(4, keys.size)
    assertEquals(VaultPointerKey.key(owner, null, now), keys[0])
    assertEquals(VaultPointerKey.key(owner, device, now), keys[2])
  }

  @Test
  fun `rejects an invalid owner`() {
    try {
      VaultPointerKey.key("aa", null, now)
      fail("se esperaba un error de validación")
    } catch (expected: IllegalArgumentException) {
      assertEquals("El destinatario del puntero no es válido", expected.message)
    }
  }

  @Test
  fun `rejects an invalid device`() {
    try {
      VaultPointerKey.key(owner, "bb", now)
      fail("se esperaba un error de validación")
    } catch (expected: IllegalArgumentException) {
      assertEquals("El dispositivo del puntero no es válido", expected.message)
    }
  }
}
