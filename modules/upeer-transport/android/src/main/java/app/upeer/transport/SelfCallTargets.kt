package app.upeer.transport

import org.json.JSONObject

/**
 * Llamar a **otro dispositivo de mi propia cuenta**.
 *
 * Hasta ahora eso estaba prohibido sin más: el móvil trataba la conversación con uno mismo como un
 * bloc de notas y `startCall` cortaba con «no puedes llamarte a ti mismo». Para quien tiene la cuenta
 * abierta en el móvil y en el escritorio eso deja la función de llamadas **sin ningún uso posible**: no
 * hay otro contacto, todo es uno mismo. Probado en el dispositivo el 08/11: cuatro contactos, los cuatro
 * de la misma cuenta, ningún botón que funcione.
 *
 * La llamada de una cuenta consigo misma **sí tiene sentido** si va dirigida a un dispositivo concreto,
 * y además encaja con lo que ya existe: la oferta se reparte a todos los dispositivos de la cuenta y
 * gana el que contesta antes (§3.3). Lo que faltaba era el paso de antes: saber a qué direcciones.
 *
 * Ese dato ya está en `sync_peers`: cada dispositivo propio guardado con la dirección de donde se le
 * vio. No hace falta inventar nada.
 */
internal object SelfCallTargets {
  /**
   * Direcciones de los **demás** dispositivos de mi cuenta, sin la mía.
   *
   * Se descarta el dispositivo local: llamarse al mismo teléfono no puede funcionar (la oferta saldría
   * y volvería), y además aparecería como «ocupado» contra sí mismo.
   */
  fun addresses(peers: Collection<JSONObject>, myDeviceId: String?): List<String> {
    val out = mutableListOf<String>()
    val mine = myDeviceId?.lowercase()
    for (peer in peers) {
      val deviceId = peer.optString("deviceId").lowercase()
      if (deviceId.isEmpty() || deviceId == mine) continue
      val address = peer.optString("address").trim()
      if (!PeerAddress.valid(address)) continue
      if (out.contains(address)) continue
      out.add(address)
    }
    return out
  }

  /**
   * Si hay a quién llamar en mi propia cuenta. La UI lo usa para decidir si enseña los botones en vez
   * de esconderlos: sin otro dispositivo con dirección, una llamada a uno mismo no tiene destino.
   */
  fun canCallSelf(peers: Collection<JSONObject>, myDeviceId: String?): Boolean = addresses(peers, myDeviceId).isNotEmpty()
}
