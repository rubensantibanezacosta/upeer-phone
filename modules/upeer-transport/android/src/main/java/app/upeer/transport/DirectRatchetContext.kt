package app.upeer.transport

/**
 * Contexto del Double Ratchet para el chat directo.
 *
 * La sesión DR se indexa por `(upeerId, contextId)`, y `upeerId` es **la cuenta**. Una
 * cuenta puede tener varios dispositivos, y todos ellos hablan con la misma cuenta del
 * otro lado. Con un único contexto `direct` los dos comparten sesión: cada mensaje
 * encadena el ratchet del anterior, así que en cuanto dos dispositivos escriben, uno de
 * los dos deja de descifrar. El "escribiendo" seguía llegando porque no pasa por el
 * ratchet.
 *
 * El escritorio ya usa `direct:<deviceId>`; esto es lo mismo, para que las dos
 * plataformas hablen por el mismo canal. El sync ya lo hacía bien (`sync:<deviceId>`).
 */
internal object DirectRatchetContext {
  const val DIRECT = ProtocolStateStore.DIRECT_SESSION_CONTEXT

  /** Contexto para el dispositivo del contacto, o el histórico si no se conoce. */
  fun forDevice(deviceId: String?): String {
    val normalized = deviceId?.trim()?.lowercase().orEmpty()
    return if (normalized.isEmpty()) DIRECT else "$DIRECT:$normalized"
  }

  /**
   * Contextos que se deben probar al descifrar, del más específico al histórico.
   *
   * Una sesión establecida antes de este cambio vive en `direct`, así que se prueba
   * después del contexto por dispositivo para no dejar mensajes ilegibles.
   */
  fun candidates(senderDeviceId: String?): List<String> {
    val specific = forDevice(senderDeviceId)
    return if (specific == DIRECT) listOf(DIRECT) else listOf(specific, DIRECT)
  }
}
