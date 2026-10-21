# Protocolo de chat Desktop: contrato para la implementación móvil

Este documento registra el contrato observado en `upper-desktop/src/main_process`. No implementa ni adapta el protocolo. Una implementación móvil no se debe considerar interoperable hasta que pase pruebas cruzadas con Desktop.

## Transporte y framing

- La aplicación Desktop escucha TCP en el puerto `50005` dentro del host local; yggstack reenvía las conexiones Yggdrasil entrantes a ese listener.
- Desktop abre conexiones TCP salientes al mismo puerto mediante SOCKS5 local en `127.0.0.1:9050`.
- El listener de Desktop está ligado a `::1:50005`; yggstack hace el reenvío entre la red overlay y ese listener local.
- Cada paquete TCP tiene un prefijo de longitud de 4 bytes, entero sin signo big-endian, seguido de un objeto JSON UTF-8. La longitud máxima de frame es 10 MiB.
- El módulo Expo actual ofrece `dialTCP`, `writeTCP`, `read` y `closeConnection`, pero no ofrece un listener TCP ni eventos de conexión entrante. Por ello no implementa el lado receptor que Desktop requiere; marcar el chat como funcional con solo esas funciones sería incorrecto.

## Paquete exterior y firma

Para un mensaje directo, Desktop crea un paquete `CHAT` que incluye `id` UUID, `timestamp`, `content`, `nonce`, `ratchetHeader`, y, al iniciar una sesión, `x3dhInit`. `replyTo` es opcional.

Desktop firma el objeto canónico formado por los datos del paquete más `senderUpeerId`, `senderYggAddress` e `isInternalSync`. La serialización canónica ordena claves de objeto recursivamente, conserva el orden de arrays y omite valores `undefined`. La firma es Ed25519 codificada como hexadecimal.

Al enviar un paquete directo, Desktop lo sella para la clave pública Ed25519 del destinatario: convierte la clave a Curve25519, aplica `crypto_box_seal` al JSON firmado y transmite `{ type: "SEALED", ciphertext }`, con ciphertext hexadecimal. El receptor abre `SEALED` antes de validar la firma y procesar el paquete interior.

## Cifrado de chat

- La sesión inicial usa X3DH con claves de identidad Ed25519 convertidas a Curve25519 y una Signed Pre-Key del destinatario. El primer paquete incluye `x3dhInit: { ekPub, spkId, ikPub }`.
- Los mensajes usan el Double Ratchet de Desktop, no AES-GCM ni un DH estático por mensaje. El encabezado es `{ dh, pn, n }` dentro de `ratchetHeader`.
- El cifrado de mensaje usa `crypto_secretbox_easy` de libsodium (XSalsa20-Poly1305). Desktop persiste estado de ratchet por contacto y aplica pasos de recepción, claves omitidas y control de mensajes repetidos.
- El contenido se comprime antes del cifrado; en recepción se descifra y descomprime antes de guardarlo y mostrarlo.
- Si no existe sesión compatible, Desktop responde con `DR_RESET` y su Signed Pre-Key Bundle. No se debe sustituir este flujo por un cifrado improvisado.

## ACK y recepción

- Después de validar, descifrar y persistir el `CHAT`, Desktop responde con un paquete firmado `ACK` que incluye el mismo `id` y `status: "delivered"`.
- `ACK` también queda sellado para la clave del destinatario al enviarse de forma directa.
- Aunque el transporte del paquete Desktop se apoya en conexiones TCP/framing, los ACK y `DR_RESET` se envían mediante la ruta normal de paquetes Yggdrasil, no como una respuesta en el mismo socket TCP entrante. El bridge móvil actual expone sockets TCP/UDP de overlay, pero no un listener TCP entrante ni el envío del paquete de respuesta firmado/sealed a través del protocolo de red.

## Dependencias faltantes en el móvil

1. Listener/aceptor TCP en el puerto de protocolo, integrado con el bridge Yggdrasil y el ciclo de vida Android.
2. Implementación interoperable de Ed25519, conversión Ed25519/Curve25519, sealed boxes libsodium, X3DH, Signed Pre-Keys y Double Ratchet.
3. Persistencia transaccional y protegida del estado de ratchet, incluyendo estados de claves precompartidas y deduplicación.
4. Serialización canónica, firma y validación idénticas; framing de Desktop; compresión y semántica de ACK/DR_RESET.
5. Pruebas de integración cruzadas: Desktop→móvil, móvil→Desktop, primer contacto/X3DH, ratchet posterior, duplicados, ACK, reintentos y entradas TCP fragmentadas/coalescidas.

Hasta que estas piezas estén implementadas y verificadas, la interfaz debe presentar chat como no disponible o en desarrollo; no debe persistir mensajes como enviados ni afirmar que se entregan.