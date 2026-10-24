package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxRetryPolicyTest {
  @Test
  fun `el backoff crece con cada intento y se detiene en el máximo`() {
    assertEquals(OutboxRetryPolicy.BASE_BACKOFF_MS, OutboxRetryPolicy.backoffMs(1))
    assertEquals(OutboxRetryPolicy.BASE_BACKOFF_MS * 3, OutboxRetryPolicy.backoffMs(3))
    assertEquals(OutboxRetryPolicy.MAX_BACKOFF_MS, OutboxRetryPolicy.backoffMs(60))
  }

  @Test
  fun `un intento fuera de rango no produce esperas negativas`() {
    assertEquals(OutboxRetryPolicy.BASE_BACKOFF_MS, OutboxRetryPolicy.backoffMs(0))
    assertEquals(OutboxRetryPolicy.BASE_BACKOFF_MS, OutboxRetryPolicy.backoffMs(-4))
    assertEquals(OutboxRetryPolicy.MAX_BACKOFF_MS, OutboxRetryPolicy.backoffMs(Int.MAX_VALUE))
  }

  @Test
  fun `los errores duros se reintentan hasta el límite`() {
    assertTrue(OutboxRetryPolicy.canRetry(0))
    assertTrue(OutboxRetryPolicy.canRetry(OutboxRetryPolicy.MAX_ATTEMPTS - 1))
    assertFalse(OutboxRetryPolicy.canRetry(OutboxRetryPolicy.MAX_ATTEMPTS))
  }

  @Test
  fun `un envío sin confirmación se abandona antes que un error duro`() {
    assertTrue(OutboxRetryPolicy.canResend(0))
    assertTrue(OutboxRetryPolicy.canResend(OutboxRetryPolicy.MAX_SENDS - 1))
    assertFalse(OutboxRetryPolicy.canResend(OutboxRetryPolicy.MAX_SENDS))
    assertTrue(OutboxRetryPolicy.MAX_SENDS < OutboxRetryPolicy.MAX_ATTEMPTS)
  }
}
