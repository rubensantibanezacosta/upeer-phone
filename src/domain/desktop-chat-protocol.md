# Protocolo de chat Desktop: contrato para la implementación móvil

Este documento registra el contrato observado en `upper-desktop/src/main_process`. No implementa ni adapta el protocolo. Una implementación móvil no se debe considerar interoperable hasta que pase pruebas cruzadas con Desktop.

## Estado del port móvil (22 de octubre de 2026)

- Identidad BIP39 de 12 palabras, Ed25519 y upeer ID: implementada y con vector de derivación contra Desktop.
- Nodo Yggdrasil embebido con `yggdrasil-go 0.5.14` y netstack gVisor de `yggstack`; transporte saliente TCP/UDP, lectura, escritura y cierre operativos.
- `yggstack` expone `ListenTCP`/`ListenUDP`; el bridge publica `listenTCP`/`acceptTCP` y el servicio `:yggdrasil` mantiene un listener TCP en `50005`.
- Framing de Desktop implementado en Go: 4 bytes big-endian, JSON UTF-8, máximo 10 MiB, fragmentación/coalescencia y rechazo de longitudes inválidas.
- `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT` se validan con canonicalización compatible con Desktop y Ed25519. Hay vector cruzado y harness `transport-bridge/harness/desktop-handshake.mjs`.
- La identidad uPeer se cifra con Android Keystore, la carga `:yggdrasil` y la UI solo la provisiona; no se expone la clave privada por Binder.
- `SEALED`, conversión Ed25519/Curve25519, X3DH, Signed Pre-Keys y Double Ratchet están implementados en Go y comparados con vectores de `sodium-native`.
- El servicio responde `HANDSHAKE_ACCEPT` con SPK nativa y descifra/persiste `SEALED`/`CHAT` con peers, sesiones y mensajes cifrados. Se probaron dos mensajes consecutivos del mismo ratchet y el reinicio del servicio.
- El ACK firmado se envía tras descifrar y fue validado en dispositivo con un verificador independiente; el outbox persistente entrega chat móvil→Desktop con ACK. `DR_RESET` valida la SPK firmada, actualiza la clave solo si es más reciente, borra la sesión y reintenta el outbox. Una SPK obsoleta o repetida no reemplaza la actual ni reinicia el ratchet, tampoco desde un handshake reenviado. La compresión gzip/base64 es compatible con Desktop y las notificaciones locales se publican desde `:yggdrasil`. `DR_RESET`, ACK perdido, duplicados, frame truncado y reconexión tras cambio de red están probados en dispositivo.
- El outbox usa un pool de envío y un lock por peer: un peer inalcanzable no bloquea mensajes a otros. Tras 30 intentos, una entrada se marca `failed`, se elimina del outbox y deja de consumir reintentos. Verificado en dispositivo con una entrada obsoleta de un peer ficticio y un mensaje nuevo entregado con la SPK vigente.
- El rate limiting usa el token bucket y los límites de Desktop para `SEALED`, `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT`, `CHAT` y `ACK`; añade 20/min para `DR_RESET`, que Desktop deja sin regla. `SEALED` se limita antes de descifrar; el resto, tras validar la firma. Los frames limitados se contabilizan en `inboundStatus`/`protocolStatus` y no ejecutan efectos de estado.
- La política de replay exige `id` UUID en `CHAT` y `ACK`, trata un `id` ya persistido del mismo remitente como duplicado idempotente (responde ACK sin volver a descifrar), rechaza colisiones de `id` entre conversaciones y solo aplica un ACK a mensajes propios del remitente. `DR_RESET` sigue protegido por SPK monotónica. Hay pruebas unitarias Kotlin para límites y UUID, y el harness `send-handshake-flood.mjs` verifica que un flood autenticado no supera el límite.
- Conclusión: el protocolo headless funciona en ambos sentidos, la UI muestra la conversación y notifica mensajes entrantes; la siguiente prioridad es DHT/bootstrap, cambio de identidad del contacto y ciclo de vida/offline prolongado. Ver [`../../roadmap/implementation-order.md`](../../roadmap/implementation-order.md).

## Transporte y framing

- La aplicación Desktop escucha TCP en el puerto `50005` dentro del host local; yggstack reenvía las conexiones Yggdrasil entrantes a ese listener.
- Desktop abre conexiones TCP salientes al mismo puerto mediante SOCKS5 local en `127.0.0.1:9050`.
- El listener de Desktop está ligado a `::1:50005`; yggstack hace el reenvío entre la red overlay y ese listener local.
- Cada paquete TCP tiene un prefijo de longitud de 4 bytes, entero sin signo big-endian, seguido de un objeto JSON UTF-8. La longitud máxima de frame es 10 MiB.
- El módulo Expo ofrece `dialTCP`, `writeTCP`, `read`, `closeConnection` y las operaciones de listener (`listenTCP`, `acceptTCP`, `closeListener`, `readFrame`, `writeFrame`, `inboundStatus`). El servicio `:yggdrasil` inicia el listener en `50005` y procesa frames sin depender de React; un frame manipulado se rechaza. El chat bidireccional, el outbox y el ACK están verificados con el harness.

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
- Aunque el transporte del paquete Desktop se apoya en conexiones TCP/framing, los ACK y `DR_RESET` se envían mediante la ruta normal de paquetes Yggdrasil, no como una respuesta en el mismo socket TCP entrante. El bridge móvil ya construye, firma, sella y envía ACK/`DR_RESET` por esa ruta; el outbox persiste el chat saliente y lo reintenta.
- En móvil, el listener, la recepción, los ACK/`DR_RESET`, el outbox y la persistencia deben ejecutarse en el servicio `:yggdrasil`, no en React. Sin push, la recepción con la app cerrada depende de que ese servicio siga vivo; si muere, el emisor debe usar vault y el receptor recuperar al reconectar.

## Dependencias faltantes en el móvil

1. ~~Listener/aceptor TCP en el puerto de protocolo, integrado con el bridge Yggdrasil y el servicio `:yggdrasil`.~~ Implementado: `ListenTCP`/`AcceptTCP`, framing, validación de handshake, ACK y persistencia viven en `:yggdrasil`.
2. ~~Implementación interoperable de Ed25519, conversión Ed25519/Curve25519, sealed boxes libsodium, X3DH, Signed Pre-Keys y Double Ratchet.~~ Implementado, vectorizado e integrado en ambos sentidos.
3. Persistencia protegida de peers, estados de ratchet, mensajes y outbox. Existe deduplicación básica; falta DB relacional y prueba de fallos transaccionales.
4. Serialización canónica, firma y validación idénticas; framing de Desktop; compresión y semántica de ACK/DR_RESET. Canonicalización, firma, framing, compresión, ACK y `DR_RESET` están implementados; las SPK son monotónicas, el rate limiting usa los límites Desktop más 20/min para `DR_RESET`, y la política de replay usa UUID por conversación, ACK idempotente y propiedad del mensaje. Faltan vectores de chat completos.
5. Pruebas de integración cruzadas: Desktop→móvil, móvil→Desktop, primer contacto/X3DH, ratchet posterior, duplicados, ACK, reintentos y entradas TCP fragmentadas/coalescidas. Ambos sentidos, X3DH, ratchet, ACK, duplicados, outbox, aislamiento de peers inalcanzables, frame truncado, reconexión tras cambio de red y flood autenticado limitado están verificados.
6. Servicio de protocolo headless con identidad, ratchets, DB, outbox y notificaciones locales, capaz de recibir con la UI cerrada. La identidad, el ratchet, los mensajes, el outbox, las notificaciones y la UI ya están cubiertos; faltan DB relacional y pruebas de ciclo de vida y red adversa. Sin push, si el servicio muere la entrega depende del vault y de la reconexión.

La UI ya muestra conversaciones y envía mensajes reales; no debe presentar la entrega como garantizada si el servicio muere ni ocultar los estados pendientes.
