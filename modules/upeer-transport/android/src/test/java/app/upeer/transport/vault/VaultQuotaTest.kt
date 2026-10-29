package app.upeer.transport.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultQuotaTest {
  @Test
  fun `un contacto sin reputacion se queda en la cuota minima`() {
    assertEquals(VaultQuota.CASUAL_BYTES, VaultQuota.of(0))
    assertEquals(VaultQuota.CASUAL_BYTES, VaultQuota.of(VaultQuota.TRUSTED_SCORE - 1))
  }

  @Test
  fun `un contacto de confianza sube a 500 MB`() {
    assertEquals(VaultQuota.TRUSTED_BYTES, VaultQuota.of(VaultQuota.TRUSTED_SCORE))
    assertEquals(VaultQuota.TRUSTED_BYTES, VaultQuota.of(VaultQuota.INTIMATE_SCORE - 1))
  }

  @Test
  fun `un amigo intimo llega a 2 y medio GB`() {
    assertEquals(VaultQuota.INTIMATE_BYTES, VaultQuota.of(VaultQuota.INTIMATE_SCORE))
    assertEquals(VaultQuota.INTIMATE_BYTES, VaultQuota.of(100))
  }

  @Test
  fun `las cuotas coinciden con las del escritorio`() {
    assertEquals(50L * 1024 * 1024, VaultQuota.CASUAL_BYTES)
    assertEquals(500L * 1024 * 1024, VaultQuota.TRUSTED_BYTES)
    assertEquals(2500L * 1024 * 1024, VaultQuota.INTIMATE_BYTES)
    assertEquals(30, VaultQuota.MIN_SCORE)
  }

  @Test
  fun `el buzon usa la cuota por defecto minima`() {
    assertEquals(VaultQuota.CASUAL_BYTES, VaultMailbox.QUOTA_BYTES_PER_SENDER)
  }
}
