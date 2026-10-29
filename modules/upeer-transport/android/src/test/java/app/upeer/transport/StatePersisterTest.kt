package app.upeer.transport

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatePersisterTest {
  private val gate = Any()
  private var content = "0"
  private val writes = AtomicInteger()
  private val errors = AtomicInteger()
  private val lastWritten = java.util.concurrent.atomic.AtomicReference("")

  private fun persister(delayMillis: Long = 0L) = StatePersister(
    lock = gate,
    snapshot = { synchronized(gate) { content.toByteArray(Charsets.UTF_8) } },
    write = { bytes ->
      if (delayMillis > 0) Thread.sleep(delayMillis)
      writes.incrementAndGet()
      lastWritten.set(String(bytes, Charsets.UTF_8))
    },
    onError = { errors.incrementAndGet() },
  )

  @Test
  fun `una rafaga de cambios se guarda en un solo escrito`() {
    val persister = persister(delayMillis = 60)
    for (value in 1..40) {
      synchronized(gate) { content = value.toString() }
      persister.request()
    }

    assertTrue(persister.flush())

    assertTrue("escritos=${writes.get()}", writes.get() < 40)
    assertEquals(content, lastWritten.get())
    assertEquals(0, errors.get())
  }

  @Test
  fun `flush espera a que el ultimo estado este en disco`() {
    val persister = persister(delayMillis = 30)

    synchronized(gate) { content = "primero" }
    persister.request()
    assertTrue(persister.flush())
    assertEquals("primero", lastWritten.get())

    synchronized(gate) { content = "segundo" }
    persister.request()
    assertTrue(persister.flush())
    assertEquals("segundo", lastWritten.get())
  }

  @Test
  fun `sin cambios no se escribe nada`() {
    val persister = persister()

    assertTrue(persister.flush())

    assertEquals(0, writes.get())
  }

  @Test
  fun `un fallo al escribir se reporta y no deja el flush colgado`() {
    val persister = StatePersister(
      lock = gate,
      snapshot = { byteArrayOf(1) },
      write = { throw IllegalStateException("llavero ocupado") },
      onError = { errors.incrementAndGet() },
    )

    persister.request()

    assertTrue(persister.flush())
    assertEquals(1, errors.get())
  }

  @Test
  fun `varios hilos que piden guardar no pierden el ultimo cambio`() {
    val persister = persister(delayMillis = 1)
    val start = CountDownLatch(1)
    val workers = 8
    val threads = (1..workers).map { worker ->
      Thread {
        start.await()
        for (value in 1..25) {
          synchronized(gate) { content = "$worker-$value" }
          persister.request()
        }
      }.apply { isDaemon = true; start() }
    }
    start.countDown()
    threads.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }

    assertTrue(persister.flush())
    assertEquals(content, lastWritten.get())
  }

  @Test
  fun `tras cerrar no se escribe el estado viejo y se puede reabrir`() {
    val persister = persister(delayMillis = 20)
    synchronized(gate) { content = "viejo" }
    persister.request()
    persister.close()

    val afterClose = writes.get()
    synchronized(gate) { content = "nuevo" }

    assertEquals("no debe escribirse nada pendiente", afterClose, writes.get())
    assertTrue(persister.flush(200))

    persister.request()
    assertTrue(persister.flush())
    assertEquals("nuevo", lastWritten.get())
  }
}
