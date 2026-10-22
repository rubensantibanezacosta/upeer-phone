# Orden de portado móvil desde Desktop

Este documento define el orden de implementación. `../roadmap.md` conserva el inventario amplio de requisitos; sus números de fase y la antigua secuencia MVP no prevalecen sobre este plan.

## Estado comprobado (22 de octubre de 2026)

- **Fase 0 — parcial.** La derivación BIP39/Ed25519/upeer ID tiene vector contra Desktop. Canonicalización, firma de `HANDSHAKE_REQ`/`ACCEPT`, framing, sealed boxes, X3DH, Double Ratchet, ACK y compresión tienen vectores o pruebas cruzadas; faltan vectores de chat, rate limiting y una política general de replay.
- **Fase 1 — parcial.** Creación, recuperación y borrado de identidad; alias/avatar local; dirección de red visible; identidad uPeer cifrada en Android Keystore y cargada por `:yggdrasil`. Pendientes: dejar de serializar la clave privada en SecureStore/React, PIN/biometría y DB cifrada.
- **Fase 2 — parcial.** Nodo Yggdrasil embebido, servicio Android visible, peers y reconexión; transporte saliente TCP/UDP, listener TCP `50005`, framing, handshake accept firmado, recepción `SEALED`/`CHAT`, ACK, outbox persistente, notificaciones locales y mensajes en `:yggdrasil`. Pendientes: ciclo de vida completo y pruebas de pérdida/cambios de red; `DR_RESET`, ACK perdido y duplicados ya tienen harness y validación en dispositivo.
- **Fase 3 — parcial.** Las SPK solo avanzan y una SPK obsoleta no reemplaza la actual ni reinicia el ratchet. Siguen pendientes rate limiting, una política general de replay, DHT Kademlia, bootstrap/LAN discovery, `DHT_UPDATE` y sincronización firmada del perfil.
- **Fases 4–10 — no iniciadas.** El chat directo ya funciona en ambos sentidos y está expuesto en la UI, pero faltan grupos, archivos, llamadas, vault y notificaciones.
- La UI de móvil ya demuestra interoperabilidad básica: lista de conversaciones, chat, estados de entrega y ACK contra el servicio `:yggdrasil`; la paridad de producto y las notificaciones siguen pendientes.

## Hitos y prioridad vigente

### Regla de arquitectura: sin UI no se pierde la recepción

uPeer no usará push notifications. Por tanto, el servicio Android `:yggdrasil` debe ser el dueño del nodo, del listener TCP, del bucle de protocolo, de la identidad uPeer, de los ratchets, del outbox y de la persistencia. La UI solo lee estado y envía comandos por Binder; puede morir sin detener la recepción.

Queda fuera de esta regla todo lo que dependa de React Native, Metro o `useEffect`. El servicio ya está en proceso separado y usa `stopWithTask=false`/`onTaskRemoved`; el protocolo y sus datos ya viven allí. Queda validar el ciclo de vida completo en fabricantes y no presentar el servicio activo como transporte sano.

### P1 — Primer frame entrante Desktop→móvil

El objetivo fue cerrar el primer tramo de las fases 0 y 2: que Desktop pudiera abrir una conexión TCP contra el móvil y enviar un frame válido con el contrato de Desktop. El envío firmado móvil→Desktop se completó después en P2.

**Trabajo mínimo:**

1. Exponer `ListenTCP` del netstack de Yggdrasil en el bridge Go y una operación de aceptación que devuelva un `connectionId` reutilizando `read`/`write`/`closeConnection`.
2. Integrar el listener y el bucle de aceptación/lectura en `YggdrasilTransportService` (`:yggdrasil`), no en React; validar que sobrevive al cierre del proceso UI y a `onTaskRemoved`, sin presentar el servicio activo como transporte sano.
3. Implementar framing de Desktop: prefijo big-endian de 4 bytes, JSON UTF-8, máximo 10 MiB, soporte de frames fragmentados y varios frames en un mismo chunk, rechazo de longitudes inválidas y cierre controlado.
4. Validar la firma con la clave pública incluida en el handshake y comprobar que el `upeerId` deriva de ella; la UI no participa.
5. Portar canonicalización y firma Ed25519 para `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT`; validar antes de despachar.
6. Crear un harness reproducible Desktop→móvil para frame partido, frames coalescidos, frame demasiado grande y cierre.

**Estado (21/10/2026): completado en dispositivo.** Desktop abrió TCP `50005` vía el SOCKS5 de yggstack, envió un `HANDSHAKE_REQ` de 828 bytes y el servicio validó la firma. Se repitió tras eliminar la tarea/UI, con `:yggdrasil` vivo, y un frame manipulado fue rechazado. El harness está en `transport-bridge/harness/desktop-handshake.mjs`.

**Criterio de salida:** el harness entrante pasa, el móvil no acepta un frame sin firma válida y el listener sigue aceptando con el proceso UI cerrado. El envío firmado móvil→Desktop se cerró en P2 y hoy la UI muestra la conversación; no se debe presentar la entrega como garantizada si el servicio muere.

**No hacer todavía:** grupos, archivos, llamadas, DHT o sincronización de perfil. Esas fases dependen de este hito y de la criptografía de sesión.

### P2 — Protocolo headless persistente (siguiente prioridad)

1. [x] Custodiar la identidad uPeer en Keystore y cargarla en el servicio; la UI solo la provisiona.
2. [x] Portar Ed25519/Curve25519, sealed boxes, X3DH, Signed Pre-Keys y Double Ratchet con vectores.
3. [x] Persistir peers, sesiones de ratchet, mensajes cifrados y outbox.
4. [x] Cablear recepción `SEALED`/`CHAT`, X3DH/Double Ratchet, reinicio de sesión y ACK verificado; compresión saliente/entrante validada. `DR_RESET` valida la SPK, reinicia la sesión y reintenta el outbox; también se ignora una SPK obsoleta.
5. [x] Envío firmado móvil→Desktop de chat, respuestas/ACK y outbox desde el servicio.
6. [x] Exponer conversaciones, mensajes, estados de entrega y no leídos a la UI.
7. [x] Endurecer recepción y entrega:
   - [x] Notificaciones locales en `:yggdrasil` con canal propio, previsualización privada en bloqueo, deep link al chat y acción «Marcar como leído». Verificado con la UI en segundo plano; abrir el chat cancela la notificación.
   - [x] Compresión compatible con Desktop (`❣UP❣` + gzip/base64, umbral UTF-16 de 512): vector Go, harness de entrada y prueba en dispositivo (1304 caracteres → 88 bytes en cable → 1304 al mostrar).
   - [x] `DR_RESET` adverso en dispositivo: emisión, recepción, SPK obsoleta, sesión borrada y reintento del outbox.
   - [x] ACK perdido y duplicado: harness con `UPEER_SKIP_ACK_FIRST`, reenvío del mismo `id` y ACK del duplicado sin descifrarlo dos veces.
   - [x] Outbox concurrente por peer y presupuesto de 30 intentos: un peer inalcanzable no bloquea mensajes a otros peers y las entradas agotadas se marcan `failed` y salen del outbox. Verificado en dispositivo con una entrada obsoleta hacia `abab…`.
   - [x] Pérdida de frames y cambios de red sin reiniciar el proceso: frame truncado con cierre controlado y frame válido posterior; Wi-Fi/datos desactivados y reactivados con peers reconectados y chat verificado, mismo PID de `:yggdrasil`.
8. [ ] DHT Kademlia, bootstrap y roaming.
9. [ ] Paridad social, vault, grupos, archivos y llamadas.

**Estado (22/10/2026):** identidad uPeer cifrada en Keystore, provisionada por la UI y recuperada por `:yggdrasil` tras `force-stop` + reinicio. Las primitivas `SEALED`/X3DH/Double Ratchet pasan vectores de `sodium-native` y sesiones completas en Go. El servicio responde `HANDSHAKE_ACCEPT`, recibe `CHAT`, persiste peers/sesión/mensajes, envía ACK, entrega mensajes salientes con outbox persistente y publica notificaciones locales; el chat en ambos sentidos, la compresión, `DR_RESET`, el ACK perdido y los duplicados están verificados. Las SPK solo avanzan, también en handshakes repetidos, y una SPK obsoleta no reinicia el ratchet. El outbox entrega en paralelo por peer y falla entradas agotadas sin bloquear al resto. La pérdida de frames y el cambio de red sin reiniciar el proceso ya están validados en dispositivo.

**Criterio de salida:** con la UI cerrada, un `CHAT` válido se descifra, persiste y notifica; al reabrir aparecen los pendientes y la identidad no se serializa en React.

## Secuencia obligatoria

### 0. Contrato común y pruebas de interoperabilidad

Congelar formatos/versiones de identidad, serialización canónica, firmas, paquetes, límites y errores mediante vectores de Desktop. Documentar qué operaciones criptográficas conserva cada dispositivo. No modificar derivaciones ni crear un protocolo móvil alternativo.

**Estado actual:** parcial. Existen vectores de derivación, canonicalización/firma de handshake, framing, sealed boxes, X3DH, Double Ratchet, ACK, compresión gzip compatible con Desktop y harness bidireccional. El rate limiting por origen/identidad y la política de replay (UUID por conversación, ACK idempotente y SPK monotónica) están implementados con pruebas unitarias Kotlin y harness de flood; los vectores de chat completos siguen pendientes.

**Salida:** los vectores son reproducibles en los entornos elegidos y existe un harness Desktop↔móvil. Toda fase posterior añade casos a ese harness.

### 1. Identidad y perfil local seguros

Cerrar la identidad ya prototipada: creación, recuperación, desbloqueo, bloqueo, borrado y manejo de secretos; separar claves privadas del estado React. Persistir alias y avatar según el modelo Desktop. Guardar metadatos pequeños en almacenamiento seguro y blobs de imagen en almacenamiento privado de app con límites, migración y borrado. Mostrar dirección de red cuando el nodo la proporcione.

El perfil en esta etapa es **local**. No afirmar que el alias/foto ya se sincroniza. No duplicar la identidad criptográfica en un segundo almacén de perfil.

**Estado actual:** parcial. Identidad, perfil local, borrado y vault nativo en Keystore funcionan; el servicio carga la identidad sin que la UI se la pase en cada arranque. La clave privada aún se serializa en estado React/SecureStore y faltan PIN, biometría y DB cifrada.

**Salida:** vectores de identidad pasan; reinicio conserva identidad y perfil; recuperación y borrado funcionan; ninguna clave privada se serializa en UI/logs.

### 2. Transporte real y ciclo de vida Android

Completar sockets de overlay para los puertos/protocolos de Desktop: recepción TCP, respuestas, framing, límites, timeouts, cierre y UDP requerido. Integrarlos con el servicio Android visible y la reconexión. La prueba de eco existente no equivale a transporte uPeer.

**Estado actual:** parcial. Salida TCP/UDP, listener TCP `50005`, framing, handshake accept, `SEALED`/`CHAT`, ACK, ratchet persistido, outbox concurrente, reinicio del servicio y notificaciones locales funcionan en `:yggdrasil`. `DR_RESET`, SPK obsoleta, ACK perdido, duplicados, aislamiento de peers inalcanzables, frame truncado y reconexión tras cambio de red están cubiertos.

**Salida:** Desktop y móvil intercambian frames reales en ambos sentidos; pruebas cubren fragmentación/coalescencia, pérdida, reinicio y cambios de red; servicio y salud del transporte se distinguen. Fragmentación/coalescencia, validación de handshake, ACK, duplicados, outbox, frame truncado y reconexión de red ya pasan.

### 3. Núcleo de red autenticado y descubrimiento

Portar validación de paquetes, canonicalización/firma, rate limiting y replay protection; después Kademlia, bootstrap, location blocks, `DHT_UPDATE`, renovación y roaming. Reutilizar algoritmos/formatos Desktop y sus límites.

**Estado actual:** parcial. Canonicalización, firma de handshake, vectores criptográficos, orden monotónico de SPK, rate limiting por origen/identidad y replay idempotente por UUID/ACK existen; siguen pendientes DHT, bootstrap y sincronización firmada del perfil.

**Aquí se habilita la sincronización firmada del perfil:** emitir el mismo `IDENTITY_UPDATE` firmado, publicarlo en DHT y verificarlo/guardarlo al recibirlo. No implementarlo como envío UDP ad hoc antes de disponer del receptor autenticado y del descubrimiento.

**Salida:** pruebas Desktop↔móvil de paquetes válidos e inválidos, replay, secuencia DHT y cambios de dirección; cambios de alias/avatar llegan a otro dispositivo y se restauran desde DHT.

### 4. Contactos y chat directo mínimo E2EE

Portar handshake, X3DH, signed pre-keys, conversión Ed25519/Curve25519, sealed boxes, Double Ratchet, persistencia transaccional, ACK, DR_RESET y outbox. Habilitar contactos y una conversación de texto vertical de extremo a extremo antes de sumar funciones de chat.

**Estado actual:** parcial. Las primitivas están portadas, el ratchet se persiste, el servicio descifra `CHAT` con X3DH, envía ACK, entrega mensajes salientes con outbox, notifica mensajes entrantes y la UI muestra la conversación. `DR_RESET`, SPK obsoleta, ACK perdido, duplicados, peer inalcanzable, reconexión de red, rate limiting y replay idempotente están probados; faltan cambio de identidad del contacto y offline prolongado.

**Salida:** primer contacto y mensajes posteriores pasan en ambas direcciones con Desktop; se prueba reinicio, duplicado, ACK perdido, contacto offline y cambio de clave; ratchet/outbox no pierden estado.

### 5. Paridad de chat y UX de producto

Portar historial/paginación, respuestas, edición, borrado, reacciones, recibos, presencia, búsqueda y notificaciones. Completar navegación y estados visuales contra funcionalidades ya verificadas, no contra pantallas de Desktop sin backend.

**Salida:** cada acción tiene persistencia, protocolo y pruebas cruzadas; estados offline/pendiente/entregado no se confunden.

### 6. Vault y continuidad multidispositivo

Portar almacenamiento offline, tokens, punteros DHT, recuperación de eventos/adjuntos, deduplicación y sincronización diferencial de dispositivos propios.

**Salida:** escenarios prolongados offline y recuperación Desktop↔móvil sin pérdidas ni duplicados visibles, con límites de disco/red.

### 7. Grupos y roles

Portar invitaciones, membresía, permisos, rotación de claves/epoch, fanout acotado, mensajes e historial de grupo.

**Salida:** pruebas mixtas Desktop/móvil, miembros offline y revocación confirman que un miembro expulsado no descifra mensajes futuros.

### 8. Archivos y multimedia

Portar selección/cámara, almacenamiento de blobs, chunks cifrados, hashes, reanudación, previews y notas de voz. Aplicar cuotas y limpieza desde el principio.

**Salida:** transferencias mixtas verifican hash, límites, cancelación, reanudación y recuperación offline.

### 9. Llamadas y capacidades posteriores

Portar primero audio 1:1 solo después de tener señalización autenticada, contactos estables y transporte validado. Medir codecs, latencia, batería y ciclo de vida en hardware antes de vídeo, llamadas grupales o compartir pantalla.

**Salida:** llamadas prolongadas y fallos/interrupciones probados en dispositivos reales; no basta con señalización o UI.

### 10. Endurecimiento y publicación

Completar backup/restauración, controles de privacidad, permisos, accesibilidad, auditoría de seguridad, supply chain, matrices de dispositivos, beta y requisitos de distribución. Decidir iOS a partir de una prueba técnica y revisión de entitlements; no asumir paridad automática con Android.

**Salida:** criterios de seguridad, interoperabilidad, estabilidad, distribución y soporte documentados y aprobados.

## Regla para solicitudes de funcionalidad

Ubicar cada petición en la primera fase incompleta de la que dependa. En particular, una propiedad de identidad puede tener UI y persistencia local en la fase 1, pero su sincronización con otros nodos no se considera terminada hasta la fase 3. No empezar la fase dependiente si la salida anterior no está demostrada con pruebas.