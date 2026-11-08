package app.upeer.transport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validación de direcciones de nodo.
 *
 * Al empezar a aceptar la dirección de un contacto desde un paquete de sincronización (lo firma otro
 * dispositivo, pero llega por la red) hacía falta un validador: antes entraba tal cual y acababa en el
 * estado y desde ahí en un `sendto`.
 */
class PeerAddressTest {

  @Test
  fun `acepta una direccion de Yggdrasil`() {
    assertTrue(PeerAddress.valid("200:7bba:1d01:9bb7:78bc:beae:f64a:6d59"))
    assertTrue(PeerAddress.valid("200:A15E:0000:0000:0000:0000:0000:723A"))
  }

  @Test
  fun `rechaza lo que no es una IPv6 de ocho grupos`() {
    assertFalse(PeerAddress.valid(""))
    assertFalse(PeerAddress.valid("200:7bba"))
    assertFalse(PeerAddress.valid("200::1"))
    assertFalse(PeerAddress.valid("::1"))
    assertFalse(PeerAddress.valid("no es una direccion"))
    assertFalse(PeerAddress.valid("192.168.1.1"))
  }

  @Test
  fun `rechaza cualquier cosa con espacio o salto de linea`() {
    // Un salto de línea rompe el sendto y se cuela al pintar los logs.
    assertFalse(PeerAddress.valid("200:7bba:1d01:9bb7:78bc:beae:f64a:6d59\n"))
    assertFalse(PeerAddress.valid("200:7bba:1d01:9bb7:78bc:beae:f64a:6d59 "))
  }

  @Test
  fun `rechaza una cadena gigante`() {
    // Una dirección de 10 MB no puede acabar en el estado.
    assertFalse(PeerAddress.valid("2".repeat(10_000)))
    assertFalse(PeerAddress.valid("a".repeat(PeerAddress.MAX_LENGTH + 1)))
  }

  @Test
  fun `rechaza la direccion todo ceros`() {
    assertFalse(PeerAddress.valid("0:0:0:0:0:0:0:0"))
    assertFalse(PeerAddress.valid("00:00:00:00:00:00:00:00"))
  }

  @Test
  fun `la longitud maxima da margen sobre una IPv6 real`() {
    // Una IPv6 completa son 45 caracteres como mucho; el tope dobla por seguridad.
    assertTrue(PeerAddress.MAX_LENGTH >= 45)
  }
}
