package app.upeer.transport

/**
 * Qué es una dirección de nodo válida.
 *
 * Hasta ahora no había ninguna comprobación y la dirección entraba tal cual, viniera de donde viniera.
 * Al empezar a aceptarla desde un paquete de sincronización (que lo firma otro dispositivo, pero llega
 * por la red) eso deja de ser aceptable: una cadena de 10 MB, una vacía o algo con un `\n` en medio no
 * pueden acabar en el estado y desde ahí en un `sendto`.
 *
 * El formato es una **IPv6 de Yggdrasil** (`200:....`, 8 grupos hexadecimales). Se acepta cualquier
 * dirección IPv6 con formato correcto y no solo las de la red 200::/7, porque el nodo puede estar en
 * otra red y perder la dirección por ser de otra subred es peor que guardar una de más.
 */
internal object PeerAddress {
  /** Un grupo hexadecimal de 1 a 4 dígitos, 8 grupos, separados por `:`. */
  private val IPV6 = Regex("^[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{1,4}){7}$")

  /** Tope de longitud: una IPv6 no pasa de 45 caracteres, con margen para lo que no sea la forma corta. */
  const val MAX_LENGTH = 128

  fun valid(value: String): Boolean {
    if (value.isEmpty() || value.length > MAX_LENGTH) return false
    // Un salto de línea o un espacio en medio rompen el `sendto` y además se colarían al pintar los logs.
    if (value.any { it.isWhitespace() }) return false
    if (!IPV6.matches(value)) return false
    // Todo ceros no es una dirección a la que mandar nada.
    if (value.all { it == '0' || it == ':' }) return false
    return true
  }
}
