# Protocolo de chat Desktop: contrato para la implementación móvil

Este documento registra el contrato observado en `upper-desktop/src/main_process`. No implementa ni adapta el protocolo. Una implementación móvil no se debe considerar interoperable hasta que pase pruebas cruzadas con Desktop.

## Estado del port móvil (21 de octubre de 2026)

- Identidad BIP39 de 12 palabras, Ed25519 y upeer ID: implementada y con vector de derivación contra Desktop.
- Nodo Yggdrasil embebido con `yggdrasil-go 0.5.14` y netstack gVisor de `yggstack`; transporte saliente TCP/UDP, lectura, escritura y cierre operativos.
- `yggstack` expone `ListenTCP`/`ListenUDP`; el bridge publica `listenTCP`/`acceptTCP` y el servicio `:yggdrasil` mantiene un listener TCP en `50005`.
- Framing de Desktop implementado en Go: 4 bytes big-endian, JSON UTF-8, máximo 10 MiB, fragmentación/coalescencia y rechazo de longitudes inválidas.
- `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT` se validan con canonicalización compatible con Desktop y Ed25519. Hay vector cruzado y harness `transport-bridge/harness/desktop-handshake.mjs`.
- La identidad uPeer se cifra con Android Keystore, la carga `:yggdrasil` y la UI solo la provisiona; no se expone la clave privada por Binder.
- `SEALED`, conversión Ed25519/Curve25519, X3DH, Signed Pre-Keys y Double Ratchet están implementados en Go y comparados con vectores de `sodium-native`.
- El servicio responde `HANDSHAKE_ACCEPT` con SPK nativa y descifra/persiste `SEALED`/`CHAT` con peers, sesiones y mensajes cifrados. Se probaron dos mensajes consecutivos del mismo ratchet y el reinicio del servicio.
- El ACK firmado se envía tras descifrar y fue validado en dispositivo con un verificador independiente; faltan outbox, `DR_RESET`, notificaciones y UI.
- Conclusión: el protocolo headless ya recibe chat real, pero la app aún no lo muestra. La siguiente prioridad es cerrar entrega/ACK y exponer la conversación; ver [`../../roadmap/implementation-order.md`](../../roadmap/implementation-order.md).

## Transporte y framing

- La aplicación Desktop escucha TCP en el puerto `50005` dentro del host local; yggstack reenvía las conexiones Yggdrasil entrantes a ese listener.
- Desktop abre conexiones TCP salientes al mismo puerto mediante SOCKS5 local en `127.0.0.1:9050`.
- El listener de Desktop está ligado a `::1:50005`; yggstack hace el reenvío entre la red overlay y ese listener local.
- Cada paquete TCP tiene un prefijo de longitud de 4 bytes, entero sin signo big-endian, seguido de un objeto JSON UTF-8. La longitud máxima de frame es 10 MiB.
- El módulo Expo ofrece `dialTCP`, `writeTCP`, `read`, `closeConnection` y las operaciones de listener (`listenTCP`, `acceptTCP`, `closeListener`, `readFrame`, `writeFrame`, `inboundStatus`). El servicio `:yggdrasil` inicia el listener en `50005` y procesa frames sin depender de React; un frame manipulado se rechaza. El chat todavía no es funcional porque falta el resto del protocolo.

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
- Aunque el transporte del paquete Desktop se apoya en conexiones TCP/framing, los ACK y `DR_RESET` se envían mediante la ruta normal de paquetes Yggdrasil, no como una respuesta en el mismo socket TCP entrante. El bridge móvil ya expone el listener y `writeFrame`, pero todavía falta construir, firmar, sellar y enviar el paquete de respuesta por la ruta normal.
- En móvil, el listener, la recepción, los ACK/`DR_RESET`, el outbox y la persistencia deben ejecutarse en el servicio `:yggdrasil`, no en React. Sin push, la recepción con la app cerrada depende de que ese servicio siga vivo; si muere, el emisor debe usar vault y el receptor recuperar al reconectar.

## Dependencias faltantes en el móvil

1. ~~Listener/aceptor TCP en el puerto de protocolo, integrado con el bridge Yggdrasil y el servicio `:yggdrasil`.~~ Implementado: `ListenTCP`/`AcceptTCP`, framing y validación de handshake viven en `:yggdrasil`; falta persistencia/ACK.
2. ~~Implementación interoperable de Ed25519, conversión Ed25519/Curve25519, sealed boxes libsodium, X3DH, Signed Pre-Keys y Double Ratchet.~~ Implementado, vectorizado e integrado en recepción con persistencia.
3. Persistencia transaccional y protegida del estado de ratchet, incluyendo estados de claves precompartidas y deduplicación.
4. Serialización canónica, firma y validación idénticas; framing de Desktop; compresión y semántica de ACK/DR_RESET. Canonicalización, firma de handshake y framing están implementados; faltan compresión y ACK/`DR_RESET`.
5. Pruebas de integración cruzadas: Desktop→móvil, móvil→Desktop, primer contacto/X3DH, ratchet posterior, duplicados, ACK, reintentos y entradas TCP fragmentadas/coalescidas. El frame entrante y la fragmentación/coalescencia ya están cubiertos; faltan X3DH, ratchet, ACK y el sentido móvil→Desktop.
6. Servicio de protocolo headless con identidad, ratchets, DB, outbox y notificaciones locales, capaz de recibir con la UI cerrada. La identidad, el ratchet y los mensajes ya están custodiados/persistidos; faltan DB relacional, outbox, notificaciones y UI. Sin push, si el servicio muere la entrega depende del vault y de la reconexión.

Hasta que estas piezas estén implementadas y verificadas, la interfaz debe presentar chat como no disponible o en desarrollo; no debe persistir mensajes como enviados ni afirmar que se entregan.