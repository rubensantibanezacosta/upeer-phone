# Roadmap móvil uPeer con Expo

_Estado: port en curso a 23 de octubre de 2026. Identidad, perfil local, nodo Yggdrasil, transporte saliente/entrante TCP, chat bidireccional con un contacto Desktop ya existente, paquetes DHT firmados, LocationBlock con secuencia persistente, códec/admisión DHT, núcleo Kademlia, store/lookup iterativo y bootstrap/descubrimiento local están verificados; B0–B5 están cerradas localmente. B6 (identidad de cuenta y registro de dispositivos) tiene el núcleo Go completo, clave/certificado de dispositivo, registro local firmado con epoch, guarda de admisión y un trabajador DHT que publica y consulta el registro; siguen pendientes la prueba de campo de ese trabajador, el emparejamiento OOB en UI, la revocación desde UI y la migración de la mensajería de grupos/vault. B7 tiene publicación, lookup iterativo, `DHT_UPDATE` bidireccional con certificado y registro en Android y Desktop, secuencia anti-reinstalación, renovación acotada y admisión de contactos antes de cifrar; la mensajería directa ya usa claves por dispositivo en CHAT, `DR_RESET` y `CHAT_UPDATE`, y sigue pendiente la prueba de roaming real. B8 es el servicio, B9 la interoperabilidad y B10 la sincronización multidispositivo; en B10 el enrolamiento OOB sin raíz ya está implementado en el bridge Go, expuesto por el servicio/módulo Expo y ejecutable desde la pantalla «Vincular este dispositivo», verificado contra Desktop con un vector cruzado._

> Coordinación móvil↔escritorio: el estado conjunto, las dependencias cruzadas y el trabajo abierto de B6/B7 se mantienen en [`../ROADMAP.md`](../ROADMAP.md) (raíz del workspace, fuera de este repositorio).

> Documento único de alcance y orden. Las fases **B0–B10** son la secuencia obligatoria de red, identidad de cuenta y sincronización; las **fases de producto 4–10** solo empiezan después de B9 y el **Producto 6** además después de B10. Las secciones «Fase 0» a «Fase 15» del anexo conservan requisitos detallados, pero **no son el orden de ejecución**.

## Propósito y reglas de uso

Este documento guía el desarrollo de un cliente móvil de uPeer basado en el proyecto de escritorio. Debe actualizarse al completar fases o cambiar decisiones técnicas. No se debe marcar una tarea como completada por existir una pantalla o prototipo: cada criterio de salida requiere validación en dispositivos y, cuando corresponda, interoperabilidad con el cliente de escritorio.

### Reglas para usar este roadmap

1. Antes de implementar una fase, verificar en el repositorio el estado real de las funciones de escritorio que se pretenden portar y enlazar sus módulos y pruebas relevantes en el PR.
2. No cambiar derivaciones criptográficas, serialización canónica ni formatos de paquetes sin vectores de interoperabilidad aprobados.
3. No exponer semillas, claves privadas, mensajes ni direcciones sensibles en logs, analítica o informes de fallos.
4. No presentar una conexión como activa solo porque el proceso Go o un servicio Android estén ejecutándose; verificar salud real de red y capacidad de transporte.
5. No intentar ocultar el uso de red en segundo plano ni eludir los controles del sistema operativo. La conexión debe ser opt-in, visible, revocable y explicada al usuario.
6. No afirmar que Android o iOS mantendrán siempre el proceso activo. Diseñar reconexión, persistencia y estados visibles ante suspensión o terminación.
7. No añadir dependencias, binarios Go ni módulos nativos antes de verificar licencia, procedencia, compatibilidad, tamaño y modo reproducible de construcción.
8. Cada fase debe terminar con criterios de salida, pruebas y limitaciones documentadas antes de empezar la siguiente fase dependiente.
9. Expo Go no es el entorno de validación final para este proyecto: los módulos nativos de Yggdrasil y Android requieren development builds y prebuild/CNG.
10. Mantener el producto de escritorio funcional e interoperable durante el desarrollo móvil.
11. Una cuenta no se propaga copiando su clave privada: cada dispositivo tiene claves propias y un certificado de dispositivo; la raíz de cuenta solo autoriza o revoca.
12. La DHT descubre direcciones y publica punteros; la entrega offline se apoya en un buzón/vault cifrado por dispositivo, nunca en almacenar mensajes en la DHT.

## Estado actual del port (22 de octubre de 2026)

| Área | Estado | Evidencia y límite |
|---|---|---|
| Identidad local | Parcial | BIP39 de 12 palabras, Ed25519, upeer ID, recuperación y borrado. Vector de derivación Desktop en `transport-bridge/bridge_test.go`; la clave se cifra en Keystore y la carga `:yggdrasil`, y la UI solo la provisiona. Faltan PIN/biometría y cambio de identidad del contacto. |
| Perfil local | Hecho | Alias y avatar en SecureStore; imagen en archivo privado de la app. Aún no se publica ni sincroniza. |
| Contactos | Parcial | Alta manual `upeerID@dirección`, SQLite en WAL y borrado. Sin QR, solicitudes, reputación ni bloqueo; el handshake ya se ejecuta en el servicio al enviar/recibir. |
| Nodo Yggdrasil | Hecho en dispositivo | `yggdrasil-go 0.5.14` + netstack gVisor de `yggstack`; nodo embebido con `libgojni.so`, peers y reconexión. |
| Transporte saliente | Hecho | `dialTCP`, `dialUDP`, `read`, `write` y `closeConnection`; paquetes firmados con outbox persistente, reintentos con backoff y ACK. Chat móvil→Desktop verificado con el harness. |
| Transporte entrante | Hecho | Listener TCP `50005` en `:yggdrasil`, framing 4B/10 MiB con fragmentación y coalescencia, `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT`, X3DH, `SEALED`/`CHAT`, ACK y persistencia. Chat Desktop→móvil verificado, incluido frame truncado y reconexión tras cambio de red; falta estrés prolongado. |
| Servicio Android | Parcial | Foreground service visible en proceso separado `:yggdrasil`, `stopWithTask=false`, configuración e identidad cifradas con Keystore, parada manual y recuperación tras force-stop. Faltan batería y control on/off explícito. |
| Segundo plano sin push | Parcial | Listener, identidad, protocolo y outbox viven en `:yggdrasil`; el servicio responde handshakes, descifra `SEALED`/`CHAT`, envía ACK, persiste sesión/mensaje y publica notificaciones locales aunque la UI esté cerrada. Faltan pruebas prolongadas de batería y red. |
| Protocolo uPeer | Parcial | `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT` firmados con SPK nativa, X3DH, `SEALED`/`CHAT`, ACK, compresión y outbox validados contra un peer Desktop simulado en ambos sentidos; `DR_RESET`, ACK perdido, duplicados, rate limiting por origen/identidad y política de replay por UUID y propiedad del mensaje probados. Las SPK solo avanzan en handshakes; un `DR_RESET` con el mismo `spkId` se acepta como reset idempotente mediante `resetId`, y una SPK obsoleta no reinicia la sesión. El chat cruzado se verificó con un contacto ya conocido: el handshake móvil aún no envía `powProof`, obligatorio en Desktop para contactos nuevos, ni `addresses`/`ephemeralPublicKey`. El LocationBlock firmado, el `dhtSeq` persistente y el `deviceId` derivado están integrados localmente en Go, binding, Kotlin y TypeScript (B2); el códec, la admisión, el núcleo Kademlia y bootstrap/descubrimiento están portados localmente (B3–B5), mientras roaming y el trabajador DHT en el servicio siguen pendientes. Faltan alta de contacto nuevo desde móvil, prueba con Desktop real, cambio de identidad del contacto y offline prolongado. |
| Persistencia | Parcial | Estado de protocolo cifrado en Keystore con peers, sesiones de ratchet, últimos 500 mensajes y outbox; sin DB relacional ni grupos. |
| Multidispositivo | Enrolamiento OOB en Go (enrollee y autorizador) + UI de vinculación con certificado en Keystore y escaneo QR; identidad sin raíz y sincronización pendientes | Desktop tiene `deviceId`, tabla `devices`, self-sync y vault, pero el escenario multiproceso comparte mnemonic y SPK entre dispositivos; `isTrusted` no se aplica al enrutado y revocar solo borra una fila. B6 y B10 exigen claves por dispositivo, registro firmado, emparejamiento y revocación; la Fase 10 no se portará sobre el atajo actual. |
| UI | Parcial | Onboarding, perfil, contactos, conexión, mapa, lista de conversaciones y chat conectados al protocolo nativo; estados pendiente/enviado/entregado, no leídos, ACK y notificaciones locales visibles. Faltan adjuntos, búsqueda y llamadas. |

**Último hito cerrado:** P1 (primer frame entrante Desktop→móvil) y P2 (protocolo headless persistente). El detalle está en [Hitos y prioridad vigente](#hitos-y-prioridad-vigente). El siguiente bloqueo es B6 (identidad de cuenta y registro de dispositivos), seguido de B7 (DHT/roaming), B8 (servicio), B9 (interoperabilidad) y B10 (sincronización).

## Base actual de escritorio

La referencia funcional está en el repositorio desktop asociado y sus módulos de protocolo, red e identidad.

El proyecto desktop asociado incluye identidad BIP39 y upeer ID; firmas Ed25519; cifrado E2EE X25519/Salsa20/Poly1305 y ratchet; huellas y alertas de cambio de clave; transporte sobre Yggdrasil; DHT Kademlia; bootstrap y roaming firmado; contactos; presencia; chat con ACK/lectura, respuestas, reacciones, edición y borrado; vault y recuperación offline; grupos, roles y sincronización multidispositivo; transferencia cifrada de archivos; notas de voz; configuración y diagnósticos; y llamadas cuya implementación tiene partes completas y otras aún pendientes.

La sincronización multidispositivo de Desktop es una **referencia funcional parcial, no un modelo de seguridad que portar**. `src/main_process/storage/schema.ts` y `devices-operations.ts` guardan dispositivos descubiertos, pero `isTrusted` solo se usa en la UI y `deleteDevice` únicamente elimina la fila. El test `tests/integration/network/multi-process.integration.test.ts` arranca el segundo dispositivo con la **misma mnemonic y el mismo `sourceDir`** que el primero, incluido su SPK: no valida claves independientes ni autorización por dispositivo. `SYNC_PULSE` tiene handler, pero no se encontró ningún emisor (`broadcastPulse`) en `src/main_process`; la sincronización real observada es self-sync directo y vault con el mismo `recipientSid`. En online, el self-sync directo funciona si el otro dispositivo está en `selfAddresses` o es descubierto por DHT; en offline, el vault conserva el paquete para recuperación posterior, pero lo cifra con la misma identidad/SPK, sin sobre por dispositivo ni historial inicial negociado. Por tanto, B6 y B10 incluyen cambios obligatorios en Desktop, no solo el port móvil.

No todas las descripciones del README deben tomarse como prueba del estado real: antes de portar, confirmar código, pruebas y compatibilidad actual.

La integración desktop ejecuta `yggstack` y configura SOCKS local en `127.0.0.1:9050`, `-remote-tcp 50005` y `-remote-udp 50006:127.0.0.1:50006`. El nodo Yggdrasil reenvía tráfico de la red overlay a los listeners locales de uPeer; esos puertos no son necesariamente puertos abiertos en la interfaz física del teléfono. El objetivo móvil es que uPeer participe en la misma overlay y conserve sus protocolos y puertos de aplicación donde la API lo permita, no tunelizar el tráfico general del dispositivo ni presentar una VPN ficticia. Antes de elegir arquitectura hay que comprobar si el binding móvil ofrece sockets TCP/UDP de overlay directamente o solo paquetes IPv6 que requieren una interfaz TUN o una pila IP de usuario.

## Principios de arquitectura

- Compartir dominio, protocolo y vectores de prueba cuando sean independientes de Node, Electron y Chromium.
- Mantener en código nativo o almacenamiento seguro las operaciones que usan claves privadas.
- Crear contratos de plataforma para motor Yggdrasil, transporte, almacenamiento seguro, base de datos, notificaciones y captura multimedia.
- No trasladar Electron IPC, Node sockets, `child_process`, `sodium-native`, `better-sqlite3` ni APIs Chromium directamente a React Native.
- Usar Expo con development builds, módulos nativos y config plugins para cambios nativos reproducibles.
- Tratar Android e iOS como integraciones nativas distintas, aunque compartan UI y lógica de dominio.
- Preservar interoperabilidad con desktop como requisito transversal y no como una fase tardía.

## Estado de las fases bloqueantes B0–B10 (22 de octubre de 2026)

- **B0 — completada.** La derivación BIP39/Ed25519/upeer ID tiene vector contra Desktop. Canonicalización, firma de `HANDSHAKE_REQ`/`ACCEPT`, framing, sealed boxes, X3DH, Double Ratchet, ACK y compresión tienen vectores o pruebas cruzadas. Los 12 tipos DHT, LocationBlock multi-dirección/legacy y `DHT_UPDATE` tienen fixture generado por Desktop y pruebas en Go, JS y Kotlin; el validador de Desktop acepta el corpus.
- **B1 — completada localmente.** `transport-bridge/dht/` contiene canonicalización y paquetes firmados, y el binding regenerado expone `buildSignedPacket`, `verifyCanonicalJSON` y `verifySignedPacket`; `DhtPacketCodec` aplica la política en Kotlin. La prueba con dos instalaciones queda en B9.
- **B2 — completada localmente, integrada en servicio/AIDL/TS.** `transport-bridge/dht/location_block.go` construye y verifica bloques firmados con direcciones normalizadas, TTL 30/60 días, `dhtSeq`, `deviceId` y `deviceMeta`, con compatibilidad legacy. El binding gomobile expone `buildLocationBlock`, `verifyLocationBlock` y `deriveDeviceID`; `DhtLocation` y `DhtLocationStore` aplican la política en Kotlin, persisten la secuencia cifrada por identidad y el servicio la publica por AIDL. Desde B6 el `deviceId` deriva de la clave del dispositivo y el bloque adjunta su certificado; la secuencia sigue siendo estado local. Falta la prueba de campo Desktop↔móvil, que corresponde a B9.
- **B3 — completada localmente, pendiente de integración en el bucle DHT.** `dht/codec*.go` y `DhtPacketPolicy`/`DhtMessageParser` portan los 12 tipos de `protocol.ts` y los mensajes legacy con límites de 64 KiB, profundidad 16, 20 nodos, 50 peers, 10 000 caracteres de valor, claves 40/64 hex y `queryId` de 64. `DhtAdmissionPolicy` y `AdmissionPolicy` aplican los umbrales DHT de Desktop por IP e identidad, verifican firma/`senderUpeerId` y consumen respuestas solo con query pendiente. La integración con el trabajador DHT corresponde a B8 y la prueba de amplificación/fuzzing a B9.
- **B4 — completada localmente.** `dht/kademlia_*.go` y `app/upeer/transport/dht/kademlia/*.kt` implementan IDs SHA-256 truncados a 20 bytes, XOR, 160 buckets, K=20, α=3, routing con evicción LRU y `lastSeen`, store de 10 000 entradas/30 días/10 dispositivos ordenados por `dhtSeq`, queries con timeout y correlación estricta, y los handlers `PING`/`PONG`/`FIND_NODE`/`FOUND_NODES`/`FIND_VALUE`/`FOUND_VALUE`/`STORE`/`STORE_ACK`. `KademliaIterative` añade store con `FIND_NODE` previo, ACK y reintento acotado, y lookup por rondas α=3 que incorpora nodos nuevos; `KademliaValueMerge` fusiona bloques por `deviceId` y registros por `epoch`. Las pruebas simulan tres nodos en Kotlin y Go, incluidas expiración, peer que no responde, ACK perdido y dos dispositivos; el cableado al servicio y gomobile corresponde a B8 y la prueba mixta de campo a B9.
- **B5 — completada localmente.** `KademliaSeedLoader` valida `upeerId` (BLAKE2b-256 de la clave Ed25519), dirección IP literal y clave de 32 bytes; carga contactos existentes, archivo `seednodes.json` y TXT `dht-seeds.upeer.chat`, deduplica y acota a 100 seeds. `KademliaBootstrap` exige 10 contactos verificados por respuesta `FIND_NODE`, reintenta cada 30 s con backoff hasta 5 min, descubre por rondas y se recupera de reinicio mediante `KademliaContactFileStore`, que solo guarda contactos verificados y limita a 100. La sonda LAN `ff02::1` queda tras la política de multicast, permiso Wi-Fi, servicio activo, red disponible y ahorro de batería, con intervalo de 5 min. El resolvedor DNS Android usa `DnsResolver.rawQuery` y el parser TXT propio; el `MulticastLock` real, la ruta de persistencia y el worker quedan en B8. Las pruebas cubren vectores BLAKE2b, seeds válidas/inválidas, red simulada con seed Desktop-compatible hasta 10+ nodos, reinicio, backoff/cambio de red y LAN: 94 pruebas JVM y 46 Go. La prueba con un Desktop real por red queda en B9.
- **B6 — en curso (núcleo Go + almacén móvil).** `transport-bridge/dht/account_*.go` define certificado de dispositivo firmado por la raíz, solicitud de pairing OOB firmada por el dispositivo, registro de cuenta con `epoch` y revocaciones, admisión certificado→registro, `LocationBlock` firmado por clave de dispositivo con certificado adjunto y publicación/consulta del registro en Kademlia. El binding expone las operaciones desde `dht_signing.go`. En Android, `DeviceIdentityStore` cifra en Keystore una clave Ed25519 de dispositivo y su certificado, `YggdrasilIdentity` lo provisiona al cargar/provisionar la cuenta y `locationBlockJSON` publica el bloque firmado por dispositivo con certificado; el estado se expone por AIDL/TS. Faltan el emparejamiento OOB en UI, las SPK firmadas por dispositivo, la migración de Desktop/móvil y los vectores cruzados; la admisión de certificados de contactos ya se aplica en B7; el detalle está en la sección B6. Desktop ya emite certificado y registro para ubicación, pero su mensajería sigue usando la identidad compartida. Es el bloqueo activo.
- **B7 — en curso (store/lookup iterativo, `DHT_UPDATE` bidireccional y secuencia anti-reinstalación).** El `LocationBlock` firmado por dispositivo se publica bajo la clave DHT del `upeerId`, se valida al recibir `DHT_STORE` y se recupera con lookup iterativo, comprobando certificado y admisión contra el registro de cuenta. El store usa `FIND_NODE` previo, K nodos, ACK y reintento acotado; el lookup incorpora nodos nuevos por rondas α=3, fusiona los bloques de varios dispositivos y cachea solo el resultado verificado. `DHT_UPDATE` se envía a contactos íntimos con todas sus direcciones conocidas, solo al cambiar `dhtSeq` o direcciones, y en recepción exige sobre firmado, certificado, registro vigente y `dhtSeq` estrictamente mayor por `(cuenta, deviceId)`; el último aceptado se persiste en Keystore y no se reenvía. Si falta el estado local de secuencia, el worker recupera el propio bloque DHT y se niega a publicar cuando no puede leerlo o verificarlo. Desktop ya emite bloques con certificado y registro de cuenta, y acepta los de Android. La renovación con límite de saltos y la admisión de contactos antes de cifrar ya están implementadas; `ContactEncryptionAdmission` impide cifrar o reutilizar ciphertext hasta validar registro, ubicación, certificado y revocaciones, con caché de 60 s y política legacy auditada. Siguen pendientes la migración de mensajería Desktop y la prueba de roaming real. El trabajo abierto y su orden están en [`../ROADMAP.md`](../ROADMAP.md).
- **B8 — parcial.** El servicio `:yggdrasil` ya es dueño del nodo, listener, protocolo, ratchets, outbox y persistencia cifrada; falta integrar el trabajador DHT (códec, admisión, núcleo y bootstrap ya disponibles), el `MulticastLock`, la ruta de persistencia de contactos, certificados/epoch y su estado observable.
- **B9 — no iniciada.** No hay validación DHT Desktop↔móvil, fuzzing de paquetes, presupuesto de batería/red para descubrimiento ni interop del registro de dispositivos.
- **B10 — enrolamiento del móvil hecho con escaneo QR y autorizador en el bridge, identidad sin raíz y sincronización pendientes.** El bridge Go ya implementa el protocolo OOB sin raíz que usa Desktop: decodifica la invitación, firma la solicitud del dispositivo sobre el payload canónico y la sella con el secreto de un solo uso, y abre y verifica el certificado emitido por la cuenta antes de instalarlo (`ParseEnrollmentInvite`, `CryptoIdentity.BuildEnrollmentRequest`, `OpenEnrollmentCertificate`). También cubre el lado autorizador desde el bridge: `createEnrollmentInvite`, `openEnrollmentRequest` e `issueEnrollmentCertificate` generan la invitación con `inviteId` derivado y TTL acotado, abren la solicitud comprobando cuenta y ventana de 10 min, y firman y sellan el certificado; el vector de `transport-bridge/testdata/enrollment_vectors.json` verifica ambas direcciones, incluida una firma de certificado idéntica byte a byte a la de Desktop. El vector se verifica en ambos clientes. `DeviceEnrollment` guarda el estado en Keystore (solicitud pendiente + certificado instalado), el AIDL y el módulo Expo exponen `deviceEnrollmentBegin`/`…RequestJSON`/`…Finish`/`…Cancel`/`…StatusJSON`, y la pantalla única de recuperación del acceso ejecuta el flujo completo pegando códigos o escaneando con la cámara (`expo-camera`, `CameraView` con `barcodeTypes: ['qr']`) los QR que muestra el escritorio: invitación y certificado de respuesta. El reparto es asimétrico por hardware — el único dispositivo con cámara es el móvil, así que se escanea siempre escritorio → móvil y la solicitud del teléfono se copia. Falta el coordinador Kotlin y la UI del autorizador móvil (que el teléfono emita invitación y certificado para vincular un escritorio), el runtime en modo dispositivo (la app sigue necesitando la raíz para operar), la clave de cuenta pública sin mnemonic y toda la sincronización online/offline, historial y entrega diferida entre dispositivos autorizados.
- **Fases de producto 4–10 — no iniciadas como paridad.** El chat directo con un contacto ya conocido funciona en ambos sentidos y está expuesto en la UI, pero faltan grupos, archivos, llamadas, vault y notificaciones avanzadas. El Producto 6 (vault/multidispositivo) no empieza hasta B10.

### Disposición del trabajo local no integrado

| Archivo | Estado | Acción antes de continuar |
|---|---|---|
| `transport-bridge/dht_signing.go` | Integrado; adaptador gomobile | — |
| `transport-bridge/location_block.go` | Integrado; adaptador gomobile | — |
| `transport-bridge/dht_signing_test.go` | Prueba del adaptador | — |
| `DhtTypes.kt`, `DhtRoutingTable.kt`, `DhtValueStore.kt` | Retirados; reemplazados en B4 | Implementación vigente en `app/upeer/transport/dht/kademlia/`, no en la raíz |

Ningún prototipo cuenta como fase completada hasta que tenga vector Desktop, binding y prueba cruzada.

## Orden por fases bloqueantes

Cada fase B solo puede empezar cuando la anterior tenga su criterio de salida demostrado. Las fases de producto (4–10) no se adelantan para tapar huecos de red: dependen de B9; el Producto 6 depende además de B10.

| Fase | Bloquea a | Entrega mínima | Estado |
|---|---|---|---|
| B0 | B1–B10 | Vectores canónicos y harness JS/Go/Kotlin | Hecha |
| B1 | B2–B10 | Firma/verificación de paquetes DHT en el bridge y en Kotlin | Hecha |
| B2 | B4–B10 | LocationBlock firmado, `dhtSeq`, `deviceId` y TTL | Integrada localmente |
| B3 | B4–B10 | Códec DHT estricto, límites y admisión | Integrada localmente |
| B4 | B5–B10 | Kademlia: routing, store, protocolo y queries | Integrada localmente |
| B5 | B6–B10 | Bootstrap, seeds, LAN y persistencia de contactos | Integrada localmente |
| B6 | B7–B10, Producto 6 | Cuenta, claves por dispositivo, certificado, registro, emparejamiento y revocación | En curso (Go + Keystore móvil) |
| B7 | B8–B10 | Location blocks con certificado, `DHT_UPDATE`, renovación y roaming | En curso (Android + Desktop con certificado; anti-reinstalación Android hecha, falta prueba real) |
| B8 | B9–B10 | Trabajador DHT, certificados y epoch dentro de `:yggdrasil` | Parcial |
| B9 | B10, Producto 6 | Interoperabilidad Desktop↔móvil, registro, fuzzing y presupuesto | No iniciada |
| B10 | Producto 6 | Sincronización online/offline, historial y entrega diferida por dispositivo | En curso (enrolamiento móvil hecho; identidad sin raíz y sincronización pendientes) |

## Regla de organización de archivos

Desktop separa `network/dht/kademlia/*` del router, los handlers y el exchange. El port debe repetir esa separación; no se añaden archivos nuevos a la raíz de `app/upeer/transport/` ni de `transport-bridge/`.

### Android

```text
modules/upeer-transport/android/src/main/java/app/upeer/transport/
  dht/
    kademlia/
      DhtConstants.kt
      DhtDistance.kt
      DhtTypes.kt
      DhtRoutingTable.kt
      DhtValueStore.kt
      DhtProtocol.kt
      DhtPendingQueries.kt
      DhtMain.kt
    DhtLimits.kt
    DhtMessage.kt
    DhtMessageParser.kt
    DhtPacketCodec.kt
    DhtPacketPolicy.kt
    DhtAdmissionPolicy.kt
    DhtBootstrap.kt
    DhtLocation.kt
    DhtUpdate.kt
    DhtStatus.kt
  (infraestructura existente: servicio, Keystore, outbox, ratchet)
```

Los tests replican el paquete bajo `src/test/java/app/upeer/transport/dht/...`; no se mezclan tests de DHT con los de transporte en la raíz.

### Go

```text
transport-bridge/
  bridge.go                 # única fachada exportada a gomobile
  dht/
    canonical.go
    signed_packet.go
    location_block.go
    codec.go
    codec_types.go
    codec_decode.go
    codec_validation.go
    codec_exchange.go
    admission.go
    *_test.go
```

La lógica nueva de DHT va en `transport-bridge/dht/`; la raíz conserva solo los tipos y métodos que gomobile necesita exportar. Los tests viven junto al paquete que prueban.

## Detalle de fases bloqueantes

### B0 — Contrato común y vectores

**Bloquea:** todo lo demás.

- [x] Vectores de identidad, handshake, framing, sealed boxes, X3DH, Double Ratchet, ACK y compresión.
- [x] Añadir vectores generados por Desktop para: paquete canónico con objetos anidados, Unicode y enteros; `DHT_PING`/`PONG`/`FIND_NODE`/`FOUND_NODES`/`FIND_VALUE`/`FOUND_VALUE`/`STORE`/`STORE_ACK`; LocationBlock con varias direcciones y `deviceId`; LocationBlock legacy; `DHT_UPDATE`.
- [x] Ejecutar los mismos vectores en JS, Go y Kotlin y comparar bytes canónicos, no solo el resultado booleano.
- [x] Documentar campos excluidos de firma (`signature`, `contactCache`, `renewalToken`), límites de tamaño y errores esperados.

**Salida:** un paquete firmado por Desktop verifica en Go y Kotlin, y uno firmado por móvil verifica con el validador real de Desktop sin normalizaciones propias.

**No hacer:** empezar Kademlia antes de que el vector de `DHT_PING` pase en los tres runtimes.

**Estado (22/10/2026): completada.** El fixture `dht_vectors.json` cubre canonicalización, firma, 12 tipos DHT, LocationBlock multi-dirección con `deviceId`/`deviceMeta`, bloque legacy y `DHT_UPDATE`. Go, JS y Kotlin reproducen los mismos bytes y firmas; el validador de Desktop acepta el corpus. La prueba de campo con dos instalaciones queda en B9.

### B1 — Paquetes firmados canónicos en bridge y binding

**Depende de:** B0.

- [x] Prototipo Go `BuildSignedPacket`/`VerifyCanonicalJSON` con rechazo de campos reservados y JSON trailing.
- [x] Mover la lógica a `transport-bridge/dht/` y dejar en la raíz solo el adaptador gomobile.
- [x] Exportar `buildSignedPacket`, `verifyCanonicalJSON` y `verifySignedPacket` en el bridge; regenerar el binding Java.
- [x] Exponer en Kotlin un códec de paquete firmado que no permita inyectar `signature`, `senderUpeerId` ni `senderYggAddress`.
- [x] Probar en JVM: firma válida, firma alterada, clave distinta, `upeerId` que no deriva de la clave, campos reservados, JSON no objeto y datos trailing.

**Salida:** Kotlin firma un `DHT_PING` y Desktop lo acepta; Desktop firma el mismo paquete y Kotlin lo acepta.

**Estado (22/10/2026): completada.** `transport-bridge/dht/` contiene canonicalización y paquetes firmados; la raíz conserva el adaptador gomobile. El binding regenerado expone `buildSignedPacket`, `verifyCanonicalJSON` y `verifySignedPacket`, y `DhtPacketCodec` aplica la política en Kotlin con pruebas JVM de firma válida, alteración, clave distinta, `upeerId` incoherente, campos reservados, dirección inválida, JSON no objeto y trailing. La firma JDK ya reproduce los bytes de Desktop en `DhtCanonicalVectorTest`; la prueba con dos instalaciones queda en B9.

### B2 — LocationBlock, secuencia y dispositivo

**Depende de:** B1.

- [x] Prototipo Go de construcción y verificación, TTL 30/60 días, normalización, deduplicación y orden de direcciones, `deviceId` máximo 128 y compatibilidad legacy.
- [x] Exportar construcción/verificación al binding y añadir el códec Kotlin equivalente.
- [x] Persistir `dhtSeq` monotónico por identidad; incrementarlo una sola vez por cambio real de dirección y conservarlo tras reinicio.
- [x] Derivar un `deviceId` estable por instalación desde la clave de dispositivo, nunca desde la UI.
- [x] Verificar el bloque derivando el `upeerId` de la clave pública; no confiar en el campo del bloque.
- [x] Probar bloque expirado, TTL fuera de rango, direcciones inválidas/duplicadas, secuencia menor, salto de secuencia abusivo, `deviceId` largo y bloque legacy.

**Salida:** un cambio de dirección genera un bloque verificable por Desktop, con secuencia creciente y sin aceptar un bloque antiguo o de otra clave.

**Estado (22/10/2026): completada localmente.** `transport-bridge/dht/location_block.go` construye y verifica bloques firmados y el adaptador gomobile los expone como `buildLocationBlock`, `verifyLocationBlock` y `deriveDeviceID`. `DhtLocation` replica el códec canónico en Kotlin y `DhtLocationStore` persiste de forma cifrada `dhtSeq` por identidad. La secuencia solo avanza al cambiar la dirección, se conserva al reiniciar, se reinicia al cambiar de identidad, se recupera del propio bloque DHT si falta el estado local y aplica el límite `MAX_DHT_SEQ_JUMP` de Desktop. El servicio publica el bloque y el estado por AIDL y el módulo TypeScript los tipa. `DhtLocationTest`, `DhtLocationStoreTest`, `DhtSequenceTest` y las pruebas Go cubren vectores de Desktop, expiración, clave distinta, campos alterados, `dhtSeq` retrocedido, salto abusivo, TTL, direcciones inválidas, bloque legacy y recuperación de secuencia. Desde B6 el `deviceId` lo aporta `DeviceIdentityStore` y el bloque adjunta el certificado del dispositivo. La prueba de campo Desktop↔móvil queda en B9.

### B3 — Códec DHT y política de admisión

**Depende de:** B1.

- [x] Portar los tipos exactos de `kademlia/protocol.ts` y los mensajes legacy de `dhtExchange.ts`/`legacyHandlers.ts`.
- [x] Fijar límites de nodos por respuesta, tamaño de valor, longitud de claves, `queryId`, `nodeId`, direcciones y JSON total.
- [x] Aplicar rate limiting DHT por origen y tipo con los mismos umbrales que Desktop antes de asignar memoria o despachar.
- [x] Rechazar tipos desconocidos, respuestas sin query pendiente y paquetes con `senderUpeerId` incoherente.
- [x] Añadir pruebas de paquete vacío, gigante, anidado, truncado y con campos de más.

**Salida:** un corpus inválido no provoca asignaciones desmedidas ni respuestas de amplificación; cada paquete válido se decodifica igual que en Desktop.

**Estado (22/10/2026): completada localmente.** `DhtLimits`, `DhtMessage`, `DhtMessageParser` y `DhtPacketPolicy` decodifican los 12 tipos DHT con rechazo de campos no declarados, JSON de más de 64 KiB, profundidad mayor de 16, claves de 40/64 hex, `queryId` de más de 64, más de 20 nodos, más de 50 peers o valores de más de 10 000 caracteres. `DhtPendingQueries` correlaciona respuestas modernas por `queryId` y `DHT_RESPONSE` legacy por `targetId`, con origen y timeout de 30 s. `DhtAdmissionPolicy` aplica rate limiting por IP e identidad con los umbrales exactos de `rate-limiter.ts`, verifica firma, deriva el remitente de la clave y rechaza `senderUpeerId` incoherente o respuestas sin query. El port Go replica el códec, la admisión y las pruebas en `transport-bridge/dht/codec*.go` y `admission.go`. Las 58 pruebas JVM y toda la suite Go pasan; la integración en `DhtProtocol` queda para B4 y el fuzzing/amplificación para B9.

### B4 — Núcleo Kademlia

**Depende de:** B2, B3.

- [x] Portar IDs de 160 bits (SHA-256 truncado a 20 bytes), XOR, 160 buckets, K=20 y α=3.
- [x] Implementar routing table con evicción del contacto más antiguo, `lastSeen`, búsqueda de los K más cercanos y refresco de buckets.
- [x] Implementar value store con límite de 10 000 entradas, TTL de 30 días, deduplicación y valores multidispositivo ordenados por `dhtSeq`.
- [x] Implementar queries pendientes con timeout, limpieza y respuesta solo para la query correspondiente.
- [x] Portar los handlers `PING`, `PONG`, `FIND_NODE`, `FOUND_NODES`, `FIND_VALUE`, `FOUND_VALUE`, `STORE` y `STORE_ACK`.
- [x] Probar una red simulada de tres nodos: descubrimiento, almacenamiento, búsqueda, expiración y contacto que no responde.

**Salida:** dos instancias Kotlin y una Go intercambian `FIND_NODE`/`STORE` con los mismos resultados que Desktop y sin bloquearse por un peer caído.

**Estado (22/10/2026): completada localmente.** `transport-bridge/dht/kademlia_*.go` y `app/upeer/transport/dht/kademlia/*.kt` replican `distance.ts`, `kbucket.ts`, `routing.ts`, `store.ts` y `protocol.ts` con IDs SHA-256 truncados a 20 bytes, XOR, 160 buckets, K=20 y α=3. El routing evicta el contacto más antiguo, actualiza `lastSeen`/dirección y expone buckets obsoletos; el store aplica 10 000 entradas, TTL de 30 días, deduplicación y hasta 10 bloques por clave ordenados por `dhtSeq` descendente. Los trackers de queries de Kotlin y Go correlacionan por `queryId` (y clave cuando existe), descartan respuestas de otra query y limpian a los 30 s; cada lookup usa timeout de 5 s. Los handlers modernos y el encoder (`DhtMessageEncoder`/`EncodeMessage`) validan la salida con el códec B3. Las pruebas de tres nodos cubren descubrimiento, `STORE`, `FIND_VALUE`, expiración y peer caído en Kotlin y Go, con un total de 79 pruebas JVM y `go test ./dht` en verde. El cableado gomobile/servicio y la prueba mixta Desktop↔móvil quedan en B8/B9.

### B5 — Bootstrap y descubrimiento

**Depende de:** B4.

- [x] Cargar seeds desde contactos existentes, archivo local y DNS TXT `dht-seeds.upeer.chat`; validar `upeerId`, dirección y clave antes de añadirlos.
- [x] Implementar bootstrap con mínimo de 10 nodos y reintento cada 30 s, con deduplicación y backoff.
- [x] Añadir descubrimiento LAN `ff02::1` solo si Android lo permite en el servicio; documentar restricciones de multicast y batería.
- [x] Persistir únicamente contactos verificados y acotar su número.
- [x] Probar arranque sin seeds, seeds inválidas, seed Desktop, reinicio y cambio de red.

**Salida:** el móvil pasa de cero a una tabla con al menos 10 nodos verificados usando un seed Desktop, y se recupera tras reiniciar el servicio.

**Estado (22/10/2026): completada localmente.** `KademliaSeedLoader`/`KademliaSeedValidator` cargan contactos existentes, archivo local y TXT, validan que la clave Ed25519 derive el `upeerId` mediante BLAKE2b-256, rechazan direcciones no literales o claves ajenas, deduplican y limitan a 100 seeds. `KademliaBootstrap` sonda `PING` + `FIND_NODE` hacia el propio nodo, solo marca contactos tras recibir respuesta, completa con rondas de descubrimiento y verificación por lotes, exige 10 contactos y aplica reintento de 30 s con backoff exponencial hasta 5 min. `KademliaContactFileStore` guarda únicamente contactos verificados, ordena por `lastSeen`, limita a 100 y usa reemplazo atómico; al reiniciar, el bootstrap restaura la tabla sin red. `KademliaLanDiscovery` aplica la guarda de multicast, permiso Wi-Fi, servicio activo, red disponible y batería, con sonda cada 5 min. Android exige `CHANGE_WIFI_MULTICAST_STATE` y `MulticastLock` (B8), el scope IPv6 de la interfaz Wi-Fi y suspender la sonda con ahorro de batería. El resolvedor Android usa `DnsResolver.rawQuery` y un parser TXT con compresión de nombres; el `MulticastLock`, la ruta real de persistencia y el worker quedan en B8. Las pruebas cubren vectores BLAKE2b, seeds válidas/inválidas, seed Desktop-compatible, red simulada de 10+ nodos, reinicio, backoff/cambio de red y LAN. Se corrigió además el tracker de queries para usar `ReentrantLock` en lugar de `CompletableFuture`, que exigía API 24 frente a `minSdk 23`. La prueba con un Desktop real por red queda en B9.

### B6 — Identidad de cuenta y registro autenticado de dispositivos

**Depende de:** B5. **Bloquea a:** B7–B10 y Producto 6.

**Decisión de diseño:** una identidad uPeer, varios dispositivos autorizados, claves independientes por dispositivo y sincronización cifrada entre ellos. El `upeerId` sigue derivando de la clave pública de la cuenta, pero cada dispositivo firma con su propia clave y presenta un certificado emitido por la raíz de cuenta. El móvil puede iniciar el emparejamiento, pero ningún dispositivo es jefe permanente ni punto de fallo obligatorio.

- [x] Fijar la custodia de la raíz de cuenta: la mnemonic/raíz se materializa solo para emparejar, revocar o firmar una actualización del registro, y no se copia como clave de mensajería a todos los dispositivos. **Decisión (22/10/2026):** custodio de emparejamiento, no clave de mensajería compartida. Cada dispositivo conserva su clave Ed25519 y su certificado; la raíz solo se usa en el dispositivo que empareja o revoca. La migración de una instalación existente puede usar temporalmente la identidad actual como raíz y emitir un certificado para una clave de dispositivo nueva, pero la raíz no se copia a los demás dispositivos. Si se decide custodiar la raíz en cada dispositivo, B6 no puede afirmar revocación efectiva.
- [ ] Definir el certificado de dispositivo firmado por la raíz: `accountUpeerId`, `deviceId` = hash de la clave pública Ed25519 del dispositivo, clave X25519 estática si existe, plataforma, capacidades, `issuedAt`, `expiresAt` y `epoch`; las SPK van firmadas por la clave del dispositivo y rotan sin reemitir el certificado, con versión y reglas de compatibilidad legacy.
- [ ] Implementar emparejamiento out-of-band (QR/código y confirmación local): el dispositivo nuevo genera sus claves y una solicitud; la operación firmada por la raíz emite el certificado. La UI puede iniciarla desde móvil o Desktop, pero no se transfieren mnemonic, clave privada de cuenta ni SPK de otro dispositivo.
- [ ] Publicar un registro de cuenta firmado y monotónico (`epoch`) con dispositivos autorizados y revocados en DHT; los contactos lo validan antes de cifrar a un dispositivo, con TTL corto y refresco al reconectar. `devices.isTrusted` local no sustituye este registro.
- [ ] Aplicar la verificación en admisión: certificado -> raíz del contacto, epoch vigente y `deviceId` no revocado. `isInternalSync` deja de ser prueba de identidad; como máximo es una pista de transporte.
- [ ] Firmar el LocationBlock con la clave del dispositivo y adjuntar su certificado; mantener la clave DHT de cuenta, varios bloques por `upeerId` y `dhtSeq` por dispositivo. La publicación y el roaming se cierran en B7.
- [ ] Definir y aplicar revocación: registro con epoch nuevo, motivo y fecha; el dispositivo revocado deja de ser destino de cifrado. Al reconectar debe descubrir el epoch, borrar claves/sesiones e impedir nuevo descifrado. Documentar la ventana de compromiso: los emisores offline con un registro anterior pueden haber cifrado al revocado y los ciphertexts ya almacenados en su dispositivo no se borran remotamente.
- [ ] Actualizar Desktop: migrar de identidad compartida a cuenta + dispositivo, eliminar el self-sync basado en mnemonic/SPK compartidas e implementar registro, emparejamiento y revocación antes de declarar paridad.
- [x] Persistir cuenta, certificado, epoch, revocados y claves de dispositivo en Keystore/almacén seguro; la UI nunca exporta claves privadas.
- [ ] Añadir vectores y pruebas: dos dispositivos con claves distintas, tercero no autorizado, certificado manipulado, epoch retrocedido, revocación online y offline, y migración de una instalación existente.

**Estado (22/10/2026): en curso, núcleo Go completo, certificado de dispositivo y registro local con guarda de admisión.** `account_types.go` fija el esquema versionado; `account_certificate.go` emite/verifica el certificado y la solicitud OOB; `account_registry.go` y `account_registry_ops.go` construyen, verifican, actualizan con `epoch` monotónico y revocan; `account_admission.go` aplica certificado→registro→revocado; `account_location.go` firma `LocationBlock` con la clave del dispositivo y adjunta el certificado; `account_dht.go` publica y recupera el registro por una clave DHT BLAKE2b de 64 hex, con límite de valor de 10 000 bytes. El binding expone estas operaciones en `dht_signing.go`. En Android, `DeviceIdentityStore` genera una clave Ed25519 de dispositivo, solicita y verifica su certificado contra la raíz, lo cifra en Keystore y lo reemite al caducar; `AccountRegistryStore` construye y cifra el registro firmado con los certificados activos, conserva revocaciones, incrementa `epoch` al cambiar un certificado o al acercarse el TTL (24 h con renovación 1 h antes) y valida la admisión del dispositivo local. `YggdrasilIdentity` refresca certificado y registro antes de enviar o publicar, `YggdrasilTransportService.sendChat`/`locationBlockJSON` bloquean operaciones si el dispositivo local no está admitido y `DhtLocation.buildDevice` firma el bloque con la clave del dispositivo. `DeviceAdmissionGuard` comprueba la admisión cacheada antes de cada frame entrante, antes de entregar el outbox y al enviar; si el registro marca revocado el dispositivo local, borra sesiones, SPK y outbox una sola vez y rechaza nuevos descifrados y envíos hasta que se vuelva a admitir. El registro se refresca al arrancar el nodo, al restaurarlo tras un reinicio y antes de enviar o publicar. La guarda local sigue siendo la única que bloquea el dispositivo revocado; la validación de certificados de contactos antes de cifrar y el roaming quedan para B7. El registro y su estado se exponen por AIDL (`accountRegistryJSON`, `accountRegistryStatusJSON`, `revokeAccountDevice`, `validateDeviceAdmission`) y TypeScript (`getAccountRegistry`, `getAccountRegistryStatus`, `revokeAccountDevice`, `validateDeviceAdmission`); `DhtLocation.verify` enruta los bloques con certificado a `VerifyDeviceLocationBlock`. Pruebas Go: dos dispositivos con claves distintas, tercero no autorizado, certificado manipulado, epoch retrocedido, revocación online/offline, metadatos de registro divergentes, bloque de ubicación manipulado y publicación/consulta Kademlia; pruebas Kotlin de parseo de certificado, secuencia, construcción/reutilización del registro, renovación por certificado, refresco por TTL, revocación del dispositivo local y de otro dispositivo, cambio de cuenta y guarda de admisión con notificación única de revocación; `ContactRegistryStoreTest` cubre adopción, persistencia, rollback, cambio en el mismo epoch, caducidad y admisión, y `KademliaPacketRouterTest` cubre enrutado, remitente desconocido y clave incoherente. El servicio arranca ahora `YggdrasilDhtRuntime` en el mismo proceso `:yggdrasil`: carga semillas por DNS/fichero/contactos persistidos, hace bootstrap con `KademliaBootstrap`, envía y recibe paquetes DHT firmados sobre el puerto 50005, exige que la clave del remitente derive su `upeerId` y valida cualquier `DHT_STORE` dirigido a la clave de registro de cuenta. Tras el bootstrap publica el registro propio y, al arrancar o cambiar la red, reintenta el bootstrap, republica y consulta los registros de hasta ocho contactos conocidos; `ContactRegistryStore` cifra esos registros remotos, exige `epoch` creciente y actualizaciones monotónicas, y responde a la admisión de certificados con el registro cacheado. El estado DHT se expone dentro de `protocolStatusJSON`. Pendiente: emparejamiento OOB en UI, prueba en dispositivo de la publicación/consulta DHT, admisión de certificados de contactos antes de cifrar, SPK firmadas por dispositivo, migración Desktop/móvil y vectores cruzados; por eso el resto de casillas sigue abierto.
**Salida:** dos dispositivos del mismo `upeerId` se emparejan sin copiar claves, generan bloques verificables con claves distintas, un tercero no puede añadirse en silencio y una revocación firmada impide que el dispositivo revocado siga siendo destinatario. Desktop y móvil aplican el mismo registro.

**No hacer:** usar `deviceId` como simple metadato de un bloque firmado por la cuenta; aceptar `isInternalSync` sin certificado; ni declarar revocación si la raíz o el SPK se comparten.

### B7 — Location blocks en DHT, `DHT_UPDATE` y roaming

**Depende de:** B2, B4, B5, B6. **Coordinación:** [`../ROADMAP.md`](../ROADMAP.md).

- [x] Publicar el bloque firmado por dispositivo con su certificado bajo la clave DHT del `upeerId`, validarlo al recibir `DHT_STORE` y recuperarlo con `FIND_VALUE` comprobando certificado y admisión contra el registro de cuenta.
- [x] Vectores cruzados con Desktop: `account_device_vectors.json` lo genera Desktop y lo verifican el bridge Go y `AccountDeviceVectorTest` en JVM; `dht-account-go.json` lo genera Go y lo verifica Vitest. Cubren certificado, registro, admisión, `accountRegistryKey`, bloque, manipulación, revocación y epoch obsoleto.
- [x] Sustituir la difusión actual por un *store* iterativo: `FIND_NODE` sobre la clave del `upeerId`, selección de los K nodos más cercanos, `STORE` con seguimiento de `DHT_STORE_ACK`, reintento acotado y poda de nodos que no responden.
- [x] Añadir un *lookup* iterativo real para `FIND_VALUE` (rondas α=3 con nodos nuevos) y cachear el resultado solo tras verificar certificado, firma y admisión; fusionar los bloques de varios dispositivos antes de elegir el `dhtSeq` más alto.
- [x] Portar `DHT_UPDATE` firmado a contactos íntimos: enviarlo al cambiar dirección/`dhtSeq` a todas las direcciones conocidas del contacto; recibirlo solo con firma de paquete válida, bloque con certificado del `deviceId` correcto, cuenta correcta, epoch no revocado y `dhtSeq` estrictamente mayor por `(cuenta, deviceId)`; persistir el último `dhtSeq` aceptado y no reenviar en bucle. El verificador de `powProof` queda desactivado hasta integrar Argon2id en Android.
- [x] Garantizar la secuencia anti-reinstalación: si `DhtLocationStore` no tiene estado, `DhtLocationWorker` recupera el suelo de `dhtSeq` desde el propio bloque verificado en DHT y falla cerrado si no puede leerlo o verificarlo; un resultado vacío se interpreta como primera publicación de ese dispositivo. `DhtUpdateAcceptanceStore` migra a estado v2 y cuenta la secuencia por `(cuenta, deviceId)`, de modo que un dispositivo nuevo no colisiona con otro y el mismo `deviceId` sigue rechazando repeticiones y retrocesos.
- [x] Implementar renovación acotada: TTL máximo 60 días, renovación solo dentro de la ventana previa a expirar, `dhtSeq` monotónico con salto máximo `MAX_DHT_SEQ_JUMP`, sin renovar bloques sin certificado vigente o con epoch revocado.
- [ ] Portar `DHT_QUERY`/`DHT_RESPONSE`/`DHT_EXCHANGE`, vecinos y verificación por clave pública y certificado de dispositivo.
- [ ] Redescubrir la dirección tras cambio de red en menos de 60 s y republicar en bootstrap, cambio de red y mantenimiento sin superar el presupuesto de batería/datos.
- [x] Definir y aplicar la admisión antes de cifrar a un contacto: refrescar registro de cuenta y ubicación si la caché expiró, fail-closed si falta certificado, admisión o epoch válido, y política de compatibilidad temporal con Desktop legacy. `ContactEncryptionAdmission` se ejecuta en `YggdrasilOutbox.deliver` antes de reutilizar ciphertext o cifrar, cachea la decisión 60 s ligada al epoch y usa `REJECT` por defecto; el servicio activa `ALLOW_WITH_AUDIT` solo para registro/ubicación ausentes y audita cada cuenta/epoch, mientras que un certificado presente inválido, caducado o revocado se rechaza siempre.
- [ ] Completar pruebas de `DHT_UPDATE` en dispositivo real: bloque antiguo, bloque de otra clave, bloque expirado, secuencia repetida, salto abusivo, certificado revocado, replay, pérdida de ACK y roaming Desktop↔móvil; incluir Wi-Fi↔datos, reinicio, Doze y cambio de IP. Las pruebas unitarias Android y Desktop ya cubren válido, repetido, retrocedido, salto abusivo, certificado inválido, cuenta ajena, registro ausente, revocación y epoch.

**Salida:** tras cambiar de red, Desktop encuentra la nueva dirección del móvil por DHT y el móvil encuentra la de Desktop, sin aceptar un bloque retrocedido y validando el certificado del dispositivo. Desktop ya emite bloques con certificado; falta la prueba de roaming en ambos sentidos.

**Estado (22/10/2026): en curso; emisión y recepción con certificado y vectores JS↔Go↔Kotlin completados.** `DhtLocationWorker` publica el bloque firmado por el dispositivo con su certificado bajo la clave del `upeerId` (compatible con `toKademliaId` de Desktop) tras el bootstrap, al cambiar de red y en el mantenimiento; no publica bloques sin certificado de dispositivo y evita republicar si la dirección, el `dhtSeq` y la expiración siguen vigentes. Los vectores `account_device_vectors.json` (Desktop→Go) y `dht-account-go.json` (Go→Desktop) fijan el contrato de certificado, registro, admisión, clave DHT y bloque firmado por dispositivo; `AccountDeviceVectorTest` consume el vector Desktop→Go en JVM y valida derivaciones BLAKE2b, firmas Ed25519, admisión, bloque, manipulación, revocación y epoch obsoleto con el verificador Kotlin puro, mientras que la invocación instrumentada del puente nativo queda para la prueba en dispositivo. Si `DhtLocationStore` no tiene estado, recupera el suelo de `dhtSeq` desde el propio bloque verificado en DHT antes de construir el siguiente y falla cerrado cuando no puede leerlo o verificarlo. `DhtContactLocation` selecciona el bloque de mayor `dhtSeq` entre varios dispositivos, exige que el certificado corresponda al `deviceId` y a la cuenta, y `lookupContactLocation` solo lo devuelve si `verifyDeviceLocationBlock` valida firma y admisión contra el registro de cuenta. El router rechaza `DHT_STORE` de ubicación sin certificado o firma válidos. El store es iterativo y el lookup usa rondas α=3 con fusión por `deviceId`; `DhtLocationCache` conserva solo el bloque verificado, como máximo 5 minutos y nunca más allá de `expiresAt`. `DHT_UPDATE` se envía por el dispatcher a contactos íntimos y se acepta en `DhtUpdateService` con certificado, registro, secuencia persistida por `(cuenta, deviceId)`, migración v1→v2, rate limit y sin reenvío; Desktop emite y verifica bloques con certificado y publica su registro. La renovación acotada y la admisión antes de cifrar ya están implementadas: `ContactEncryptionAdmission` se ejecuta en `YggdrasilOutbox.deliver` antes de reutilizar un paquete o cifrar, refresca registro y ubicación cuando faltan o caducan, cachea la decisión 60 s ligada al epoch y rechaza certificados inválidos, caducados o revocados; el servicio usa `ALLOW_WITH_AUDIT` solo para registro/ubicación ausentes durante la transición. Pendiente: migración de mensajería Desktop y prueba de roaming. El desglose, el orden de migración y la matriz de pruebas están en [`../ROADMAP.md`](../ROADMAP.md).

### B8 — Integración en `:yggdrasil` y persistencia

**Depende de:** B7.

- [ ] Ejecutar el trabajador DHT dentro del servicio, no en React; sobrevivir a `onTaskRemoved` y al cierre de la UI.
- [ ] Persistir contactos verificados, `dhtSeq`, `deviceId`, certificado de dispositivo, epoch, revocados y bloques propios con límites y migración.
- [ ] Programar refresco de buckets y republicación con presupuesto de batería/datos; suspender cuando el usuario desactive la conexión.
- [ ] Exponer estado observable: `nodeId`, contactos, bootstrapped, último bootstrap, valores almacenados, paquetes enviados/recibidos y último error, sin secretos.
- [ ] Probar UI cerrada, reinicio del servicio, cambio de red y parada manual.

**Salida:** con la UI cerrada el DHT sigue operativo, se recupera tras reinicio, conserva certificado/epoch/revocados y la UI muestra un estado que distingue “servicio activo” de “DHT saludable”.

### B9 — Interoperabilidad y endurecimiento

**Depende de:** B8.

- [ ] Añadir DHT al harness Desktop↔móvil: bootstrap, `FIND_NODE`, `STORE`, `FIND_VALUE`, LocationBlock con certificado de dispositivo, `DHT_UPDATE` y roaming.
- [ ] Validar el registro de cuenta Desktop↔móvil: emparejamiento, epoch, certificado, revocación online/offline y migración desde una instalación con identidad compartida.
- [ ] Ejecutar fuzzing de códec y pruebas de amplificación, replay, Sybil básico y contactos maliciosos.
- [ ] Medir batería, datos y latencia de bootstrap en dispositivo real durante 24 h.
- [ ] Verificar migración de estado y ausencia de secretos en logs.
- [x] Android anuncia y consume el contrato de mensajería por dispositivo en CHAT directo: `devicePublicKey`/`deviceId`/`deviceCertificate`/SPK firmada por dispositivo en handshake, binding por contacto con invalidación de sesión al cambiar de dispositivo, firma y sealed con clave de dispositivo, resolución de SPK entrante y fallback a la identidad de cuenta.
- [x] Extender la ruta por dispositivo al `DR_RESET` directo: `BuildLinkedDrReset` firma el reset con la clave de dispositivo, Desktop y Android validan el paquete ligado, verifican la SPK contra la clave del dispositivo, actualizan `deviceSignedPreKey` e invalidan la sesión y el outbox.
- [x] Extender la ruta por dispositivo al `CHAT_UPDATE` directo: `BuildLinkedChatUpdate` cifra y firma la edición con la clave de dispositivo, Desktop y Android validan el binding y aplican versiones monótonas, y la app móvil permite editar mensajes propios con reintento desde el outbox.
- [ ] Probar el roaming real Desktop↔móvil con mensajería por dispositivo y extender la ruta a `CHAT_UPDATE`, grupos, vault y sincronización multi-dispositivo.
- [ ] No habilitar sincronización multidispositivo, punteros de vault ni presencia compartida: corresponden a B6 y B10.

**Salida:** pruebas mixtas repetibles, límites medidos y ningún bloqueo de UI; a partir de aquí se abre la paridad de producto, salvo el Producto 6, que espera a B10.

### B10 — Sincronización y entrega diferida entre dispositivos

**Estado (23/10/2026): enrolamiento móvil hecho; identidad sin raíz y sincronización pendientes.** `DeviceEnrollment` implementa el lado del dispositivo nuevo del protocolo OOB: decodifica la invitación con el bridge, firma y sella la solicitud, abre el certificado emitido por la cuenta, comprueba que `accountUpeerId` y `deviceId` corresponden a esta clave y persiste solicitud pendiente y certificado en Keystore. El servicio `:yggdrasil` lo expone por AIDL y el módulo Expo por `deviceEnrollmentBegin`, `deviceEnrollmentRequestJSON`, `deviceEnrollmentFinish`, `deviceEnrollmentCancel` y `deviceEnrollmentStatusJSON`, con los códigos convertidos a base64url en TypeScript. La pantalla «Vincular este dispositivo» del acceso ejecuta los tres pasos pegando códigos. Faltan el escaneo QR, el runtime en modo dispositivo (hoy la app sigue exigiendo la raíz para arrancar el nodo) y toda la sincronización.

**Depende de:** B9. **Bloquea a:** Producto 6.

- [ ] Crear un canal pairwise entre dispositivos con X3DH/Double Ratchet usando sus claves de dispositivo y certificados; separado de las sesiones con contactos. No reutilizar el ratchet del contacto ni la SPK de otro dispositivo.
- [ ] Definir `SYNC_ENVELOPE` firmado y cifrado con `deviceId`, `epoch`, secuencia, tipo y `eventId`/`messageId`; idempotencia por UUID. Una copia sincronizada conserva el ID y el contenido original y no crea un mensaje nuevo ni reinicia el ratchet del contacto.
- [ ] Replicar mensajes salientes/entrantes, ediciones, borrados, reacciones, lecturas y eventos de perfil/grupo a todos los dispositivos autorizados. Cada dispositivo mantiene su propia sesión con el contacto: solo el que descifró un paquete de esa sesión envía ACK y avanza su ratchet; una copia recibida por sincronización no ACK ni toca la sesión del contacto.
- [ ] Implementar entrega diferida por dispositivo (buzón/vault): sobre cifrado por dispositivo, TTL y cuotas, replicado a N custodios, puntero firmado en DHT, consulta al reconectar, ACK y borrado. La DHT solo publica punteros; no almacena mensajes.
- [ ] Sincronización inicial de historial tras el emparejamiento: transferencia por chunks con cursor y hashes, reanudable y con deduplicación. Si no hay otro dispositivo online, queda pendiente; no se copia la base de datos ni claves privadas.
- [ ] Resolver conflictos por evento con versión/epoch, tombstones y desempate determinista; deduplicar mensajes, reacciones y recibos. Un mensaje sincronizado no debe generar un segundo mensaje visible ni un `DR_RESET`.
- [ ] Definir la revocación de un dispositivo que estuvo offline días: los emisores refrescan el registro antes de cifrar a un dispositivo, los mensajes cifrados con un registro posterior al epoch no se dirigen al revocado, las entradas de buzón obsoletas expiran y el dispositivo revocado borra claves al reconectar. Medir y documentar la ventana de emisores desactualizados y qué ciphertext previo no puede borrarse remotamente.
- [ ] Ejecutar el trabajador de sincronización en `:yggdrasil`, con presupuesto de batería/datos y estados observables `pending`/`synced`/`vaulted`/`delivered`/`failed`.
- [ ] Actualizar Desktop y móvil con el mismo protocolo; retirar `SYNC_PULSE` y el self-sync con identidad/SPK compartidas o migrarlos a `SYNC_ENVELOPE`.
- [ ] Pruebas cruzadas: dos dispositivos de la misma cuenta, uno offline; envío y recepción desde cada uno; edición/borrado/lectura; duplicados; reinicio; cambio de dirección; revocación con el otro offline; Desktop↔móvil. Verificar que no hay duplicados ni reinicio de ratchet.

**Salida:** un dispositivo puede enviar/recibir con el otro apagado; al volver, recupera mensajes y eventos sin duplicados ni pérdida de estado criptográfico; un dispositivo revocado no descifra mensajes cifrados con el registro posterior al epoch, aunque haya estado días offline, y la ventana de revocación con emisores desactualizados queda medida y documentada. El flujo funciona con UI cerrada y con un único dispositivo propio online.

**No hacer:** asumir que la DHT entrega mensajes; exigir que ambos dispositivos estén conectados; copiar mnemonic/SPK; ni portar la Fase 10 antes de cerrar B6 y B10.

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

**No hacer todavía:** grupos, archivos, llamadas y sincronización de perfil. DHT se rige por B0–B9; la identidad multidispositivo se cierra en B6 y la sincronización en B10. No se adelanta roaming ni el trabajador del servicio sin cerrar B5.

### P2 — Protocolo headless persistente (chat con contacto existente completado; B6 es el siguiente bloqueo)

1. [x] Custodiar la identidad uPeer en Keystore y cargarla en el servicio; la UI solo la provisiona.
2. [x] Portar Ed25519/Curve25519, sealed boxes, X3DH, Signed Pre-Keys y Double Ratchet con vectores.
3. [x] Persistir peers, sesiones de ratchet, mensajes cifrados y outbox.
4. [x] Cablear recepción `SEALED`/`CHAT`, X3DH/Double Ratchet, reinicio de sesión y ACK verificado; compresión saliente/entrante validada. `DR_RESET` valida la SPK, reinicia la sesión (también con la misma SPK) y reintenta el outbox; una SPK obsoleta se ignora y un `resetId` repetido no vuelve a reiniciar.
5. [x] Envío firmado móvil→Desktop de chat, respuestas/ACK y outbox desde el servicio.
6. [x] Exponer conversaciones, mensajes, estados de entrega y no leídos a la UI.
7. [x] Endurecer recepción y entrega:
   - [x] Notificaciones locales en `:yggdrasil` con canal propio, previsualización privada en bloqueo, deep link al chat y acción «Marcar como leído». Verificado con la UI en segundo plano; abrir el chat cancela la notificación.
   - [x] Compresión compatible con Desktop (`❣UP❣` + gzip/base64, umbral UTF-16 de 512): vector Go, harness de entrada y prueba en dispositivo (1304 caracteres → 88 bytes en cable → 1304 al mostrar).
   - [x] `DR_RESET` adverso en dispositivo: emisión, recepción, SPK obsoleta, sesión borrada y reintento del outbox.
   - [x] ACK perdido y duplicado: harness con `UPEER_SKIP_ACK_FIRST`, reenvío del mismo `id` y ACK del duplicado sin descifrarlo dos veces.
   - [x] Outbox concurrente por peer y presupuesto de 30 intentos: un peer inalcanzable no bloquea mensajes a otros peers y las entradas agotadas se marcan `failed` y salen del outbox. Verificado en dispositivo con una entrada obsoleta hacia `abab…`.
   - [x] Pérdida de frames y cambios de red sin reiniciar el proceso: frame truncado con cierre controlado y frame válido posterior; Wi-Fi/datos desactivados y reactivados con peers reconectados y chat verificado, mismo PID de `:yggdrasil`.
8. [ ] DHT Kademlia, bootstrap y roaming. B0–B5 están cerradas localmente; B6 (identidad/registro) es el siguiente bloqueo, B7 es DHT/roaming, B8 el servicio y B9 la interoperabilidad. B10 (sincronización) solo empieza tras B9.
9. [ ] Paridad social, vault, grupos, archivos y llamadas (fases 4–10, solo tras B9; vault/multidispositivo tras B10).

**Estado (22/10/2026):** identidad uPeer cifrada en Keystore, provisionada por la UI y recuperada por `:yggdrasil` tras `force-stop` + reinicio. Las primitivas `SEALED`/X3DH/Double Ratchet pasan vectores de `sodium-native` y sesiones completas en Go. El servicio responde `HANDSHAKE_ACCEPT`, recibe `CHAT`, persiste peers/sesión/mensajes, envía ACK, entrega mensajes salientes con outbox persistente y publica notificaciones locales; el chat en ambos sentidos con un contacto ya conocido, la compresión, `DR_RESET`, el ACK perdido y los duplicados están verificados. Las SPK solo avanzan en handshakes repetidos; un `DR_RESET` con la misma SPK reinicia de forma idempotente mediante `resetId`, y una SPK obsoleta no reinicia el ratchet. El outbox entrega en paralelo por peer y falla entradas agotadas sin bloquear al resto. La pérdida de frames y el cambio de red sin reiniciar el proceso ya están validados en dispositivo.

**Criterio de salida:** con la UI cerrada, un `CHAT` válido se descifra, persiste y notifica; al reabrir aparecen los pendientes y la identidad no se serializa en React.

## Contrato de protocolo de chat Desktop

Este apartado fija el contrato observado en `upper-desktop/src/main_process` que debe reproducir el móvil. El estado comprobado está en la tabla superior y en P2; aquí se documentan formatos, límites y diferencias pendientes. Tener las mismas primitivas no demuestra interoperabilidad: hay que pasar los harness de `transport-bridge/harness/` y comparar bytes canónicos.

### Transporte y framing

- Desktop escucha TCP en `::1:50005`; `yggstack -remote-tcp 50005` reenvía a ese listener el tráfico Yggdrasil entrante. Las conexiones salientes usan SOCKS5 local en `127.0.0.1:9050` y el mismo puerto `50005`.
- Cada frame es un prefijo `uint32` big-endian con la longitud del payload seguido de un objeto JSON UTF-8. El máximo es 10 MiB (10 485 760 bytes). Se rechazan longitud cero, longitudes superiores al máximo y payload truncado; el receptor acepta frames fragmentados y varios frames coalescidos en un mismo chunk.
- `CALL_MEDIA` usa otro formato binario sobre UDP y queda fuera de este contrato. El listener, el framing y el bucle de protocolo viven en `:yggdrasil`, no en React.

### Handshake y confianza

- `HANDSHAKE_REQ` y `HANDSHAKE_ACCEPT` van firmados con Ed25519 y contienen `publicKey`, `signedPreKey: { spkPub, spkSig, spkId }` y, opcionalmente, `alias`, `avatar`, `ephemeralPublicKey` y `addresses`. `HANDSHAKE_REQ` incluye además `powProof`, obligatorio en Desktop cuando el contacto es nuevo.
- `senderUpeerId` es el BLAKE2b-256 de la clave pública Ed25519. El receptor comprueba que la firma es válida y que el `senderUpeerId` deriva de `publicKey`; no confía en un ID declarado.
- La SPK es X25519, va firmada por la identidad Ed25519 y solo se acepta si su firma es válida. `DR_RESET` aplica además monotonicidad de `spkId`; el handshake de Desktop no impone esa comprobación, así que el móvil no debe reemplazar una SPK por otra anterior al recibir un handshake.
- `powProof` es Argon2id (`OPSLIMIT_MIN`, `MEMLIMIT_MIN`) con contraseña `upeerId + t` (segundos), sal de 16 bytes, primeros 4 bits del hash a cero y formato JSON `{ s, t }`; la marca `t` tiene una vigencia de ±5 minutos. Desktop todavía acepta el formato legacy SHA-256, pero los clientes nuevos deben generar Argon2id. Un móvil que no lo envíe puede chatear con un contacto ya existente, pero Desktop rechazará su alta como contacto nuevo.

### Paquete firmado

- Un `CHAT` directo contiene `type: "CHAT"`, `id` UUID, `timestamp` en milisegundos, `content`, `nonce`, `ratchetHeader: { dh, pn, n }` y, solo al iniciar o reiniciar sesión, `x3dhInit: { ikPub, ekPub, spkId }`. `replyTo` es opcional.
- El objeto que se firma es el paquete completo sin `signature`, `contactCache` ni `renewalToken`, más `senderUpeerId`, `senderYggAddress` e `isInternalSync`. `senderYggAddress` debe ser una IPv6 de `200::/7`; `isInternalSync` es `false` para un peer y `true` para sincronización entre dispositivos propios.
- La serialización canónica ordena las claves de objeto recursivamente, conserva el orden de arrays y omite `undefined`. Los números deben ser enteros; el port Go rechaza decimales y no se deben introducir en paquetes nuevos.
- La firma Ed25519 se adjunta en hexadecimal. En paquetes directos no viaja `publicKey`: el receptor usa la clave del contacto ya conocido. Por eso no existe un `CHAT` válido de primer contacto sin handshake previo.
- Desktop y el móvil aceptan una firma legacy sin `senderYggAddress`, pero los paquetes nuevos siempre deben incluirla y firmarla.
- Para `CHAT` directo, el paquete firmado se sella con `crypto_box_seal` usando la clave Ed25519 del destinatario convertida a Curve25519. El exterior es exactamente `{ type: "SEALED", ciphertext }`, con `ciphertext` hexadecimal; no lleva `nonce` ni `senderEphPub` en claro. El receptor abre `SEALED` antes de validar la firma interior.
- `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT` y los paquetes DHT no se sellan. En la ruta actual de chat, tampoco se sellan `ACK` ni `DR_RESET`: viajan firmados y el receptor debe aceptarlos así.

### Cifrado y compresión

- La sesión inicial usa X3DH con la identidad Ed25519 convertida a Curve25519 y la Signed Pre-Key X25519 del destinatario. El primer `CHAT` incluye `x3dhInit`; los mensajes posteriores usan solo el estado del ratchet.
- Los mensajes usan el Double Ratchet de Desktop, no un DH estático por mensaje ni AES-GCM. El encabezado `ratchetHeader` contiene `{ dh, pn, n }` y el contenido se cifra con `crypto_secretbox_easy` (XSalsa20-Poly1305) usando un nonce de 24 bytes.
- El estado del ratchet se persiste por peer y se protege con un lock por peer; se aplican pasos de recepción, claves omitidas y rechazo de mensajes ya consumidos. Un fallo de descifrado no debe destruir una sesión válida.
- Antes de cifrar, el contenido se comprime con el prefijo `❣UP❣` y gzip/base64 cuando tiene al menos 512 unidades UTF-16 y el resultado comprimido es más corto. En recepción se descifra y descomprime antes de persistir y mostrar.
- Si no hay sesión compatible o el descifrado falla, se responde con `DR_RESET` y la SPK vigente; no se sustituye este flujo por un cifrado improvisado.

### ACK, `DR_RESET` y recepción

- Tras validar la firma, descifrar y persistir un `CHAT`, se responde con `{ type: "ACK", id, status: "delivered" }` firmado con la identidad propia. `READ` es un paquete distinto para recibos de lectura.
- El `ACK` y el `DR_RESET` no se envían como respuesta en el mismo socket TCP entrante: se abre una conexión saliente al `senderYggAddress` autenticado y se escribe un frame nuevo. No se sellan en la ruta actual; el receptor debe validar su firma como cualquier paquete firmado.
- `DR_RESET` contiene `type`, `resetId` UUID, `signedPreKey: { spkPub, spkSig, spkId }` y `ratchetContext: "direct"`. Se valida la firma de la SPK, el UUID y la secuencia: una SPK con `spkId` menor se ignora; un `spkId` igual con la misma SPK reinicia la sesión de forma idempotente; un `spkId` igual con otra SPK se rechaza; un `spkId` mayor actualiza la SPK. Un `DR_RESET` legacy sin `resetId` se acepta por compatibilidad, pero no se deduplica.
- La caché de `resetId` es por peer, con TTL y límite de entradas. Un reset repetido no vuelve a reiniciar el ratchet ni el outbox.
- El outbox móvil persiste el `CHAT` saliente, usa un timeout de ACK de 5 s, reintenta con backoff hasta 30 intentos y marca `failed` al agotarlos. Un peer inalcanzable no bloquea a los demás.

### Límites y admisión

| Tipo | Límite |
|---|---|
| `SEALED` | 5000/s por IP, antes de descifrar |
| `HANDSHAKE_REQ` / `HANDSHAKE_ACCEPT` | 50/min por IP e identidad |
| `CHAT` | 100/min por IP e identidad |
| `ACK` | 200/min por IP e identidad |
| `DR_RESET` | 20/min por IP e identidad |

- Los límites de identidad se aplican después de validar la firma; `SEALED` se limita por IP antes de la operación criptográfica. Los paquetes limitados se cuentan en `inboundStatus`/`protocolStatus` y no ejecutan efectos de estado.
- La política de replay exige `id` UUID en `CHAT` y `ACK`. El móvil trata un `id` ya persistido del mismo remitente como duplicado idempotente y lo ACKea sin volver a descifrar; Desktop puede descifrar para confirmar el duplicado, pero no debe volver a mostrarlo ni persistirlo dos veces. Una colisión de `id` entre conversaciones se rechaza; un `ACK` solo actualiza mensajes propios de ese remitente.
- Desktop limita el contenido de chat a 1 000 000 de unidades UTF-16. El outbox móvil es más restrictivo (256 KiB de bytes UTF-8) y la fachada Go admite hasta 9 MiB de texto plano (bytes UTF-8); no se debe confundir el máximo de frame de 10 MiB con el máximo de mensaje.

### Estado y pendientes de interoperabilidad

- Verificado: chat directo en ambos sentidos con un contacto ya conocido, X3DH, ratchet posterior, compresión, ACK, `DR_RESET`, ACK perdido, duplicados, frame truncado, cambio de red y outbox concurrente.
- Pendiente inmediato: enviar el `powProof` Argon2id en `HANDSHAKE_REQ` para que Desktop acepte un contacto nuevo creado desde móvil. Sin él, el alta se rechaza aunque la firma sea válida.
- Pendiente de handshake: incluir `addresses`, `ephemeralPublicKey` y `avatar` cuando el contrato de producto los requiera; hoy el móvil solo envía lo mínimo para un contacto existente.
- Faltan vectores completos de `CHAT` generados por Desktop con claves fijas y comparación de bytes, además de vectores de contenido comprimido y de `DR_RESET` en los tres runtimes.
- Faltan DB relacional y pruebas de fallos transaccionales; la persistencia actual es cifrada, con límite de 500 mensajes y deduplicación básica.
- Faltan pruebas prolongadas de ciclo de vida, batería, red adversa y offline; sin push, si `:yggdrasil` muere la entrega depende del vault y de la reconexión.
- La UI no debe presentar la entrega como garantizada si el servicio muere ni ocultar los estados pendientes.

## Contrato canónico de paquetes DHT

Los vectores fijos están en `transport-bridge/testdata/dht_vectors.json`. Se generaron con el `canonicalStringify` real de Desktop y `sodium-native`, usando una semilla de prueba pública. Go, JS y Kotlin canonizan y firman los mismos bytes; el validador de Desktop acepta las 17 firmas y los 12 paquetes DHT del fixture. Las pruebas viven en `transport-bridge/dht_vectors_test.go`, `src/domain/canonical-json.test.ts` y `modules/upeer-transport/android/src/test/java/app/upeer/transport/dht/DhtCanonicalVectorTest.kt`.

### Objeto firmado

- Se parte del paquete DHT completo. Se excluyen solo los campos de primer nivel `signature`, `contactCache` y `renewalToken`; los objetos anidados no se limpian.
- Se añaden `senderUpeerId`, `senderYggAddress` e `isInternalSync` (si falta, `false`). El emisor no puede inyectar esos campos.
- La firma es Ed25519 sobre los bytes UTF-8 del JSON canónico. El paquete enviado conserva los campos excluidos y añade `signature`.
- La verificación reconstruye el mismo objeto. Por compatibilidad, si falla con `senderYggAddress`, se prueba el payload legacy sin esa dirección. `senderUpeerId` debe derivar de la clave pública del contacto; no se confía en el campo recibido.
- En `DHT_UPDATE`, el `locationBlock` completo forma parte de la firma exterior, incluidos `renewalToken` y `deviceMeta` anidados. La firma propia del bloque se verifica aparte.

### JSON canónico

- La raíz debe ser un objeto JSON; se rechazan arrays, escalares, JSON vacío y datos trailing.
- Las claves de objeto se ordenan por unidades UTF-16, igual que `Array.prototype.sort()` de JavaScript. No basta ordenar por bytes UTF-8: `😀` va antes que `\uE000`.
- Los arrays conservan su orden. Los strings se serializan en UTF-8 con los escapes de `JSON.stringify` (`\"`, `\\`, `\b`, `\f`, `\n`, `\r`, `\t`, `\u00xx` para controles).
- Los números deben ser enteros representables en 64 bits (JS exige `Number.isSafeInteger`). Se rechazan floats, exponentes no enteros y valores fuera de rango.
- `null`, booleanos y strings vacíos son válidos como valores. Un campo ausente y un campo `null` son distintos.
- Los codecs del port rechazan JSON no canónico antes de firmar; no se normaliza silenciosamente un payload ajeno.

### LocationBlock

- Datos firmados del formato actual: `upeerId`, `addresses`, `dhtSeq`, `expiresAt`, `deviceId` opcional y `deviceMeta` opcional. `address` se deriva de `addresses[0]`.
- `addresses` se recorta, valida como IPv4/IPv6, se deduplica y se ordena con la prioridad de Desktop (Yggdrasil primero). Un bloque sin direcciones válidas se rechaza.
- `expiresAt` es obligatorio para verificar y debe ser futuro. La construcción usa 30 días por defecto y nunca supera 60 días.
- `deviceMeta` puede incluir `clientName`, `clientVersion`, `platform`, `deviceClass` y `capabilities[]`. Forma parte de la firma del bloque.
- `alias`, `renewalToken`, `powProof` y `address` no forman parte de la firma del bloque actual. `renewalToken` sí queda cubierto por la firma exterior cuando viaja dentro de `DHT_UPDATE` o `DHT_RESPONSE`.
- Formato legacy: `{upeerId, address, dhtSeq, expiresAt}`. Solo se acepta si el bloque no tiene `deviceId` ni `deviceMeta`; si falla la firma actual se prueba esta variante.
- El prototipo Go limita `deviceId` a 128 caracteres y `dhtSeq` a valores no negativos. La monotonía de `dhtSeq` y el `deviceId` estable son responsabilidad de B2.

### Tipos y límites actuales

| Tipo | Campos y límites vigentes en Desktop y en el port desde B3 |
|---|---|
| `DHT_PING` | `type`; `queryId` opcional de hasta 64 caracteres; `nodeId` opcional 1-128 hex. |
| `DHT_PONG` | `nodeId` opcional 1-128 hex; `queryId` opcional de hasta 64 caracteres. |
| `DHT_FIND_NODE` | `targetId` 1-128 hex; `queryId` opcional de hasta 64 caracteres. |
| `DHT_FOUND_NODES` | `nodes` obligatorio, máximo 20; cada `upeerId` 1-128 hex y `address` de hasta 100 caracteres; `publicKey`/`nodeId` opcionales de hasta 128. |
| `DHT_FIND_VALUE` | `key` de 40 o 64 hex; `queryId` opcional de hasta 64 caracteres. |
| `DHT_FOUND_VALUE` | `key` opcional de 40/64 hex; exige `value` o `nodes`; `value` serializado máximo 10 000 caracteres; `nodes` máximo 20; `publisher` máximo 128; `timestamp` entero no negativo. `signature` es la firma exterior validada por B1. |
| `DHT_STORE` | `key` de 40/64 hex; `value` serializado máximo 10 000 caracteres; `ttl` opcional entre 0 y 2 592 000 s; `publisher` máximo 128 y `timestamp` entero no negativo. |
| `DHT_STORE_ACK` | `key` de 40/64 hex; `queryId` opcional de hasta 64 caracteres. |
| `DHT_QUERY` | `targetId` 32-128 hex; `referralContext` debe ser objeto si aparece; `queryId` opcional de hasta 64 caracteres. |
| `DHT_RESPONSE` | `targetId` 32-128 hex; `locationBlock` opcional validado si existe; `neighbors` máximo 20 y cada entrada con campos opcionales; `publicKey` máximo 128. |
| `DHT_UPDATE` | `locationBlock` obligatorio: `address` de hasta 100 caracteres, `dhtSeq` entero no negativo, `signature` de 128 caracteres y `powProof` de hasta 256 caracteres si existe. |
| `DHT_EXCHANGE` | `peers` obligatorio, máximo 50; cada `upeerId` 32-128 hex y `publicKey` de 64 caracteres; `locationBlock` opcional validado. |

- Desde B3 el port rechaza campos no declarados, limita el JSON total a 64 KiB en Go y 64 Ki caracteres en Kotlin, y la profundidad a 16 antes de decodificar. El límite de valor sigue siendo caracteres del JSON serializado, no bytes.
- `queryId`, `publisher`, `timestamp`, `publicKey` y `nodeId` de nodos ya tienen cota explícita; no se deben relajar por conveniencia.

### Errores esperados

- JSON raíz no objeto, datos trailing, número no entero, firma distinta de 64 bytes, clave pública distinta de 32 bytes y `senderUpeerId` que no deriva de la clave.
- `senderYggAddress` que no es IPv6 de Yggdrasil, `type` ausente, `signature`/`senderUpeerId`/`senderYggAddress` inyectados por el llamador, bloque expirado, `dhtSeq` negativo, dirección inválida o lista de direcciones vacía.
- Un paquete firmado manipulado se rechaza antes de ejecutar handlers. Un `DHT_UPDATE` con firma exterior válida pero bloque interior inválido no debe aceptarse como actualización de ubicación.

## Fases de producto (solo después de B9; Producto 6 después de B10)

B0–B9 son la secuencia obligatoria para red, identidad de cuenta e interoperabilidad; B10 es la sincronización multidispositivo. Las fases siguientes describen producto y no deben adelantarse para compensar una salida incompleta de B9. Las fases de identidad, transporte e integración Android ya tienen su estado en B0–B10; el Producto 6 no se porta sobre el atajo actual de Desktop.

### 4. Contactos y chat directo mínimo E2EE

Portar handshake (incluido `powProof` Argon2id para contactos nuevos), X3DH, signed pre-keys, conversión Ed25519/Curve25519, sealed boxes, Double Ratchet, persistencia transaccional, ACK, DR_RESET y outbox. Habilitar contactos y una conversación de texto vertical de extremo a extremo antes de sumar funciones de chat.

**Estado actual:** parcial. Las primitivas están portadas, el ratchet se persiste, el servicio descifra `CHAT` con X3DH, envía ACK, entrega mensajes salientes con outbox, notifica mensajes entrantes y la UI muestra la conversación. `DR_RESET`, SPK obsoleta, misma SPK idempotente por `resetId`, ACK perdido, duplicados, peer inalcanzable, reconexión de red, rate limiting y replay idempotente están probados. Queda pendiente el alta de un contacto nuevo desde móvil: `HANDSHAKE_REQ` no incluye el `powProof` Argon2id que Desktop exige para contactos nuevos, ni `addresses`/`ephemeralPublicKey`; el chat cruzado actual usa un contacto ya conocido. También faltan cambio de identidad del contacto y offline prolongado.

**Salida:** primer contacto y mensajes posteriores pasan en ambas direcciones con Desktop; se prueba reinicio, duplicado, ACK perdido, contacto offline y cambio de clave; ratchet/outbox no pierden estado.

### 5. Paridad de chat y UX de producto

Portar historial/paginación, respuestas, edición, borrado, reacciones, recibos, presencia, búsqueda y notificaciones. Completar navegación y estados visuales contra funcionalidades ya verificadas, no contra pantallas de Desktop sin backend.

**Salida:** cada acción tiene persistencia, protocolo y pruebas cruzadas; estados offline/pendiente/entregado no se confunden.

### 6. Vault y continuidad multidispositivo (Producto 6; depende de B10)

B6 aporta cuenta, registro autenticado de dispositivos, claves independientes y revocación; B10 añade canales pairwise y entrega diferida. Esta fase porta la capa de producto: vault, tokens, punteros DHT, recuperación de eventos/adjuntos, deduplicación, cuotas y UI de estados. No se reimplementa el self-sync actual de Desktop con identidad/SPK compartidas.

**Salida:** escenarios prolongados offline y recuperación Desktop↔móvil sin pérdidas ni duplicados visibles, con límites de disco/red; un dispositivo revocado deja de recibir y descifrar mensajes cifrados con el registro posterior al epoch.

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

Ubicar cada petición en la primera fase B incompleta de la que dependa. Una propiedad de identidad puede tener UI y persistencia local antes de B6, pero su sincronización con otros nodos no se considera terminada hasta B9; la identidad por dispositivo, el emparejamiento, la revocación y la sincronización de historial se cierran en B6 y B10. No empezar la fase dependiente si la salida anterior no está demostrada con pruebas.

## Anexo — Inventario de requisitos por área

Este anexo conserva el detalle histórico de las fases 0–15. Sirve para consultar requisitos y criterios de salida, no para decidir el orden: el orden vigente es B0–B10 y, después, las fases de producto 4–10.

| Bloque vigente | Requisitos detallados en el anexo |
|---|---|
| B0 | Fases 0, 1 y 4: alcance, contratos, vectores, identidad |
| B1 | Fases 1, 4 y 7: canonicalización, firmas, paquetes |
| B2 | Fases 4 y 7: identidad, LocationBlock, secuencia |
| B3 | Fase 7: códec, límites y admisión |
| B4 | Fases 1 y 7: DHT Kademlia y protocolo |
| B5 | Fases 2 y 7: bootstrap, descubrimiento y red |
| B6 | Fases 4 y 10: identidad de cuenta, clave por dispositivo, registro, emparejamiento y revocación |
| B7 | Fase 7: location blocks con certificado, `DHT_UPDATE` y roaming |
| B8 | Fases 3, 5, 6 y 7: app nativa, persistencia y servicio `:yggdrasil` |
| B9 | Fases 2, 7 y 15: interoperabilidad, registro de dispositivos, fuzzing y pruebas |
| B10 | Fases 5 y 10: sincronización online/offline, historial y entrega diferida |
| Producto 4 | Fases 4, 8 y 9: identidad, UI, contactos y chat |
| Producto 5 | Fase 9: paridad de chat y notificaciones |
| Producto 6 | Fases 5 y 10 + B6 y B10: persistencia, vault y multidispositivo |
| Producto 7 | Fase 11: grupos y roles |
| Producto 8 | Fase 12: archivos, multimedia y notas de voz |
| Producto 9 | Fase 13: llamadas y compartir pantalla |
| Producto 10 | Fases 14 y 15: seguridad, backup, beta y publicación |

> **Aviso:** las casillas de este anexo reflejan el inventario original. No se reinterpretan como estado actual; el estado comprobado está en la tabla superior y en las fases B0–B10.

### Fase 0 — Alcance, decisiones y viabilidad de distribución

#### Objetivo

Resolver prioridades del producto y restricciones de plataforma antes de construir la aplicación completa.

#### Tareas

- [ ] Decidir si el primer objetivo es Android, Android+iOS o una prueba técnica de ambos.
- [ ] Decidir si el cliente se distribuirá por Google Play, App Store, canales privados o varios.
- [ ] Definir si la conexión debe funcionar solo con la app en primer plano o también mientras está en segundo plano.
- [ ] Decidir qué funciones son imprescindibles para el primer lanzamiento: chat directo, grupos, vault, archivos, voz y llamadas.
- [ ] Definir los estados de conexión: desconectado, solicitando permiso, conectando, conectado, reconectando, pausado, detenido por el sistema y error recuperable.
- [ ] Definir que "conectado" requiere motor operativo, peers alcanzables y transporte funcional, no solo servicio activo.
- [ ] Revisar requisitos de Google Play para servicios Android de red en segundo plano y foreground services; revisar requisitos Apple solo si iOS y su integración de red entran en alcance.
- [ ] Especificar divulgación de actividad de red, datos, permisos, notificaciones y desconexión manual; no declarar VPN salvo que la arquitectura técnica finalmente la requiera.
- [ ] Crear matriz de riesgos, dispositivos objetivo y criterios de éxito medibles.

#### Criterios de salida

- [ ] Aprobado el alcance del MVP y la prioridad de plataformas.
- [ ] Confirmados los requisitos de distribución, firmas y entitlements.
- [ ] Aprobado el diseño de conexión transparente, visible y revocable.
- [ ] No se promete ejecución móvil permanente ni inmunidad frente al sistema operativo.

### Fase 1 — Inventario y separación del núcleo compartido

#### Objetivo

Identificar qué código puede compartirse y especificar contratos entre JavaScript, React Native y las capas nativas.

#### Tareas

- [ ] Clasificar módulos desktop como reutilizables, abstraíbles, portables con adaptación o nativos/no portables.
- [ ] Inventariar dependencias de `node:net`, `node:dgram`, `child_process`, `fs`, `Buffer`, Electron IPC y APIs Chromium.
- [ ] Inventariar bindings nativos: `sodium-native`, SQLite/SQLCipher, FFmpeg, WebCodecs y herramientas de empaquetado.
- [ ] Definir paquetes compartidos para protocolo, identidad, criptografía, DHT, dominio y tipos.
- [ ] Definir contratos `YggdrasilService`, `P2PTransport`, `SecureStorage`, `EncryptedDatabase`, `Notifications` y `MediaCapture`.
- [ ] Crear vectores de prueba desktop para BIP39/derivación, upeer ID, canonicalización, firmas, cifrado, ratchet, archivos y paquetes de grupo/vault.
- [ ] Ejecutar vectores desde Node y establecer cómo se ejecutarán desde Android e iOS.
- [ ] Documentar versionado de protocolo, detección de capacidades y estrategia de migración.
- [ ] Definir límites de memoria y validación para paquetes externos.

#### Criterios de salida

- [ ] Existe un mapa de módulos y dependencias revisado.
- [ ] Los contratos de plataforma están versionados y probados con implementaciones mock.
- [ ] Los vectores de interoperabilidad están guardados y tienen resultados conocidos del cliente desktop.
- [ ] Se identifica cualquier módulo cuya licencia o dependencia impida su uso móvil.

### Fase 2 — Prueba técnica del motor Yggdrasil y Go

#### Objetivo

Comprobar en hardware real si las integraciones móviles de Yggdrasil pueden soportar el transporte y el ciclo de vida requerido por uPeer.

#### Android

- [ ] Revisar el proyecto oficial `yggdrasil-android`, la revisión de `yggdrasil-go`, licencia, API y proceso de construcción, separando el wrapper Go reutilizable de la app de referencia que integra Android `VpnService`.
- [x] Inspeccionar la API upstream del wrapper móvil y del núcleo Go: `mobile.Yggdrasil.Send/Recv` intercambia paquetes IPv6 completos; `core.Core` expone `ReadFrom/WriteTo` como `net.PacketConn` dirigido a claves Yggdrasil, no sockets TCP/UDP de aplicación.
- [x] Confirmar que `yggquic` permite streams QUIC sobre `core.PacketConn`, pero no sustituye transparentemente los sockets TCP y UDP actuales de uPeer.
- [x] Validar la viabilidad arquitectónica del adaptador: upstream `yggstack` ya implementa una NIC gVisor sobre `ipv6rwc`, TCP/UDP y sockets `gonet`, sin TUN; sus opciones `-remote-tcp/-remote-udp` enrutan IP de overlay al listener local.
- [x] Ejecutar una prueba de extremo a extremo contra el `yggstack` empaquetado: un harness temporal crea identidad, conecta un nodo Go/gVisor al yggstack `1.0.5` y verifica eco TCP `50005` y UDP `50006`; pasó tres ejecuciones consecutivas. No requiere identidad productiva ni depende de una clave conocida para UDP.
- [x] Crear un bridge gomobile de prueba en `transport-bridge` para ciclo de vida del nodo, consulta de identidad/peers y sockets TCP/UDP con IDs opacos.
- [x] Crear módulo Expo local Android `modules/upeer-transport` y comprobar que autolinking detecta `app.upeer.transport.UPeerTransportModule`.
- [x] Verificar con `gobind` que el bridge exporta métodos Java usando tipos compatibles; probar sockets, ciclo de vida y tamaños de datagrama en tests Go.
- [x] Configurar `expo.autolinking.nativeModulesDir` para `./modules`; verificar Expo autolinking y que Gradle incluya el proyecto local `:upeer-transport`.
- [x] Compilar el binding gomobile `arm64-v8a` y validar las llamadas Kotlin/Expo contra las clases Java generadas.
- [x] Integrar el contenido del AAR Go en la biblioteca Expo (JAR de bindings y `jniLibs/arm64-v8a`); compilar `:upeer-transport:assembleDebug` y la app `:app:assembleDebug`.
- [ ] Integrar framing de mensajes TCP de uPeer y formato/fragmentación CALL_MEDIA UDP con los handlers desktop existentes.
- [x] Fijar Go `1.26.0` (toolchain automático `1.26.9` al invocar gomobile), `golang.org/x/mobile v0.0.0-20261011034528-197770444867`, Android platform 35, Build Tools 35.0.1 y NDK `27.2.12479018`; el módulo Android fija `ndkVersion` a la misma revisión.
- [x] Construir desde fuente el AAR `modules/upeer-transport/android/libs/upeer-transport.aar` para `arm64-v8a` con API Android 23; verificar que contiene `libgojni.so`, bindings Java y que sus firmas coinciden con la API llamada desde Kotlin.
- [x] Generar el APK debug Expo para Android API 37 y verificar el paquete y la inclusión de `libgojni.so` arm64; `expo export --platform android` empaqueta correctamente el bundle Hermes.
- [ ] Probar creación de configuración, arranque/parada, peers, cambio de conectividad y recuperación tras suspensión.
- [x] Verificar si la API expone sockets TCP/UDP de overlay utilizables por uPeer; inspeccionar específicamente `Send`/`Recv`, listeners, conexiones salientes y transporte de paquetes IPv6. Resultado: el wrapper pasa paquetes IPv6 completos; `core.Core` soporta `net.PacketConn` para paquetes dirigidos a una clave, no `net.Listener` TCP/UDP de aplicación.
- [ ] Probar el intercambio de TCP y UDP de aplicación sobre la overlay sin redirigir el tráfico general del teléfono.
- [x] Confirmar gVisor como implementación de referencia para la pila IP de usuario: upstream `yggstack/src/netstack` ya hace el puente de paquetes IPv6 entre `ipv6rwc.ReadWriteCloser` y sockets TCP/UDP `gonet`, con `HandleLocal` y sin VPN/TUN.
- [x] Probar TCP `50005` y UDP `50006` contra el `yggstack` de desktop; los dos mapeos pasan eco de ida y vuelta en tres ejecuciones consecutivas del harness `/transport-check`.
- [ ] Validar el framing TCP y los datagramas CALL_MEDIA con los protocolos reales de uPeer; el harness actual verifica la interoperabilidad de transporte con cargas de eco.
- [ ] Si el binding solo expone paquetes IPv6, comparar una pila IP de usuario con un TUN limitado a rutas Yggdrasil; no seleccionar `VpnService` ni declarar una VPN como requisito sin justificarlo con resultados.
- [ ] Confirmar que los sockets de peering salen por la red física y que la solución no crea routing recursivo.
- [ ] Medir tamaño de artefactos, consumo, temperatura, memoria y batería.
- [ ] Fijar hashes, procedencia y SBOM de los artefactos generados.

#### iOS, si está en alcance

- [ ] Revisar el proyecto oficial `yggdrasil-ios`, API Swift, framework Go y extensión `NetworkExtension`.
- [ ] Confirmar App Groups, entitlements, cuenta de organización y provisioning profiles requeridos.
- [ ] Probar ciclo de vida de app y extensión, suspensión, expiración, reconexión y comunicación entre procesos.
- [ ] Confirmar que el diseño es compatible con las capacidades de red y distribución previstas.

#### Decisión de empaquetado

- [x] Preferir biblioteca/framework móvil compilado desde Go frente a empaquetar un ejecutable Go independiente. Implementado como AAR gomobile con `libgojni.so`.
- [ ] Comparar upstream oficial, fork mínimo o bindings propios según API, sockets, mantenimiento y licencia.
- [ ] Definir actualizaciones de upstream, parches, builds reproducibles y revisión de seguridad.
- [ ] Verificar compatibilidad entre API del motor móvil, `yggstack` desktop y transporte actual de uPeer.

#### Criterios de salida

- [x] Un dispositivo Android físico conecta a la red Yggdrasil y expone el estado de forma fiable. Validado en el dispositivo de desarrollo; falta la matriz de fabricantes y el ciclo de vida completo.
- [x] Un nodo desktop puede intercambiar al menos un flujo de prueba a través de la integración elegida. Eco TCP/UDP contra `yggstack` Desktop; no es todavía un frame del protocolo uPeer.
- [ ] Se documenta la API de transporte disponible, la ruta de los sockets TCP/UDP de uPeer y cómo se evita routing recursivo sin tunelizar tráfico ajeno a Yggdrasil.
- [ ] Si iOS es requisito, existe una prueba equivalente y confirmación de entitlements.
- [x] Se toma una decisión documentada de reutilización o extensión del wrapper móvil. Se usa `yggdrasil-go` + netstack gVisor de `yggstack` mediante un bridge propio.

### Fase 3 — Aplicación Expo, módulos nativos y CI

#### Objetivo

Crear la base de la app y un pipeline capaz de compilar y probar código nativo, sin depender de Expo Go.

#### Tareas

- [x] Crear `apps/mobile` con Expo, React Native y TypeScript. Implementado en la raíz del proyecto `upper`.
- [x] Configurar Expo development client, development builds y prebuild/CNG para Android.
- [ ] Crear módulos Expo locales para los bridges Android/iOS. Existe `modules/upeer-transport` para Android; iOS no está iniciado.
- [ ] Automatizar config plugins para permisos, servicios, extensiones, entitlements y configuración nativa.
- [ ] Crear bridge tipado con comandos, eventos, errores, timeout y versión de API. Hay comandos tipados; faltan eventos, timeouts y versión de API.
- [x] Añadir mocks de módulo nativo para desarrollo UI y pruebas unitarias. `UPeerTransportModule.web.ts` cubre el desarrollo web.
- [ ] Configurar CI con versiones fijadas de Node, Go, SDK/NDK, Java y Xcode.
- [ ] Compilar artefactos nativos desde fuente y verificar sus hashes en CI.
- [ ] Crear builds de debug, QA y release firmadas según cada plataforma.
- [ ] Verificar que la app instalada funciona sin Metro.

#### Criterios de salida

- [x] La build Expo instala y se comunica con el módulo nativo en dispositivo físico. APK debug con `libgojni.so` arm64 validado.
- [ ] La build de CI es reproducible dentro de los límites definidos.
- [ ] Los cambios generados por prebuild no requieren edición manual sin automatizar.
- [ ] El estado de errores del módulo nativo se refleja correctamente en JavaScript.

### Fase 4 — Identidad y custodia de claves

#### Objetivo

Crear/importar identidades interoperables sin exponer material privado al renderer.

#### Tareas

- [x] Portar generación e importación de frase BIP39 de 12 palabras, validación y confirmación de respaldo.
- [x] Preservar derivación exacta de Ed25519/X25519 y upeer ID del desktop. Vector de derivación validado en `transport-bridge/bridge_test.go`.
- [ ] Implementar operaciones privadas en módulo nativo o backend de seguridad aislado. Hoy la derivación se ejecuta en JS con `@noble`; el bridge Go tiene derivación, pero no es la ruta usada por la app.
- [x] Guardar claves con Android Keystore/Keychain según capacidades disponibles. SecureStore para identidad, perfil y semilla del nodo.
- [ ] Proteger material local con claves ligadas al dispositivo cuando sea viable.
- [ ] Implementar bloqueo/desbloqueo por PIN y biometría como control local de acceso.
- [ ] Implementar flujo de revelado de semilla autenticado y con limpieza de memoria/UI.
- [ ] Revisar clipboard, screenshots, logs, backups automáticos y pantalla de tareas recientes.
- [ ] Probar importación móvil→desktop y desktop→móvil con los vectores de la Fase 1.
- [ ] Probar reinstalación, cambio de dispositivo, rotación de claves y recuperación.

#### Criterios de salida

- [ ] Una identidad creada/importada en móvil conserva el mismo upeer ID que desktop. El vector de derivación ya coincide; falta la prueba UI móvil↔Desktop.
- [ ] Firmas y operaciones criptográficas pasan los vectores cruzados.
- [ ] Las claves privadas no aparecen en logs ni estado serializado de React. Hoy `UPeerIdentity.secretKey` se serializa y pasa por estado React; es un bloqueante de la custodia.
- [ ] El bloqueo impide operaciones sensibles desde la app.

### Fase 5 — Persistencia cifrada, migraciones y archivos locales

#### Objetivo

Portar almacenamiento y estado de sesión a una base de datos compatible y protegida en móvil.

#### Tareas

- [ ] Evaluar SQLite/SQLCipher compatible con Expo prebuild para Android e iOS.
- [ ] No asumir que compartir ORM equivale a compartir implementación de cifrado.
- [ ] Portar esquema de contactos, mensajes, grupos, reacciones, transferencias, ratchets, vault, reputación, outbox y dispositivos.
- [ ] Reutilizar migraciones Drizzle solo donde el driver móvil lo soporte; definir alternativa versionada si no.
- [ ] Separar datos reconstruibles, sincronizados, secretos, blobs y ajustes locales.
- [ ] Configurar transacciones, WAL, índices, límites de crecimiento y compactación.
- [ ] Definir cuotas de multimedia y limpieza de caché por usuario.
- [ ] Implementar borrado de cuenta/chat/contacto/grupo incluyendo blobs y miniaturas.
- [ ] Diseñar backup/exportación cifrada compatible o declarar explícitamente que no se soporta inicialmente.
- [ ] Probar cierre durante escritura, disco lleno, DB dañada, restauración del OS y migraciones.

#### Criterios de salida

- [ ] Datos sensibles permanecen cifrados en reposo según la arquitectura aprobada.
- [ ] Migraciones son idempotentes y recuperables.
- [ ] No se pierden estado de ratchet ni mensajes tras terminación abrupta.
- [ ] Existen límites visibles para almacenamiento de blobs.

### Fase 6 — Servicio Yggdrasil en segundo plano en Android

#### Objetivo

Mantener la participación voluntaria del nodo Yggdrasil y la comunicación uPeer en Android mediante mecanismos del sistema que correspondan al uso real. Esta fase no presupone una VPN ni el enrutamiento del tráfico general del teléfono.

#### Contrato de segundo plano sin push

Restricción de producto: uPeer **no usará push notifications**. La recepción depende de mantener vivo el servicio en primer plano y el nodo Yggdrasil. Si el sistema mata el servicio, no hay entrega instantánea; el emisor debe usar vault y el receptor recuperar al reconectar.

**Debe vivir en el proceso de servicio (`:yggdrasil`), no en React:**

- Nodo Yggdrasil, peer manager, reconexión y salud real del transporte.
- Listener TCP `50005`, UDP `50006`, framing y bucle de recepción.
- [x] Validación/firma, `SEALED`, X3DH, Double Ratchet, ACK, `DR_RESET` y outbox portados en el bridge nativo y el servicio; falta DHT.
- [x] Acceso a la identidad uPeer desde Keystore en el servicio; falta mover el estado de ratchet y evitar que React serialice la clave privada.
- Persistencia durable de mensajes, ratchets, outbox y metadatos. El servicio es el único escritor; la UI lee y envía comandos.
- Notificaciones locales de mensajes y del estado de conexión, con previsualización configurable.

**Puede morir con la UI:**

- React Native, navegación, tema, snackbars, mapa, QR, pickers y estado de pantalla.
- Metro y cualquier tarea que dependa de un `useEffect` o de la app en primer plano.

| Evento | Comportamiento objetivo |
|---|---|
| App a segundo plano | Servicio, nodo y protocolo siguen activos. |
| Swipe desde recientes | Servicio sigue: `stopWithTask=false` y `onTaskRemoved` reafirman el foreground. |
| Muerte del proceso UI | El servicio en `:yggdrasil` sigue y puede recibir. |
| Muerte del servicio | `START_STICKY` intenta recrearlo y restaurar la configuración cifrada; puede fallar. |
| Force-stop | Se detiene todo; Android no permite reanudar hasta abrir la app. |
| Reinicio del dispositivo | Hoy no hay autoarranque. Al abrir la app se restaura; decidir si se añade opt-in con `BOOT_COMPLETED` y validar la política. |
| Acción "Desconectar" | Detiene servicio, nodo y recepción; borra la configuración de restauración. |
| Sin permiso de notificaciones | No se inicia el servicio; la UI explica que la conexión visible es obligatoria. |

**Estado actual:** ya existen el proceso separado `:yggdrasil`, `stopWithTask=false`, `onTaskRemoved`, `START_STICKY`, la configuración e identidad cifradas con Keystore, el listener TCP `50005`, el framing, la validación de handshakes, la respuesta `HANDSHAKE_ACCEPT` firmada con SPK nativa, la recepción `SEALED`/`CHAT`, el ACK, el outbox persistente, las notificaciones locales y la persistencia cifrada de sesiones/mensajes. Se probó con la tarea/UI eliminada, tras reiniciar el proceso, con dos mensajes consecutivos del mismo ratchet y tras cambio de red. El rate limiting por origen/identidad y la política de replay por UUID y propiedad del mensaje están implementados y cubiertos con pruebas unitarias. Faltan DHT/bootstrap, alta de contacto nuevo desde móvil (`powProof`), cambio de identidad del contacto y estrés offline prolongado.

**Límites explícitos:** no se promete disponibilidad permanente, entrega instantánea si el servicio muere, funcionamiento tras force-stop ni recepción tras reinicio sin abrir la app. Tampoco se mantiene la UI de React viva para aparentar conexión.

#### Tareas

- [x] Prototipar servicio Android visible en proceso separado para el bridge Yggdrasil, con Binder IPC, `stopWithTask=false`, `onTaskRemoved` y acción de desconexión en la notificación.
- [x] Guardar la configuración Yggdrasil de restauración cifrada con Android Keystore; no persistir semillas/configuración en texto claro.
- [x] Mover al servicio el listener TCP, el framing, el bucle de recepción y la validación de `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT`, con harness reproducible y prueba con la UI eliminada.
- [x] Mover al servicio la criptografía de sesión y la recepción `SEALED`/`CHAT`.
- [x] Mover el ACK y el outbox al servicio y verificarlos en dispositivo con un peer Desktop simulado; falta DHT.
- [x] Custodiar la identidad uPeer en Android Keystore y cargarla desde `:yggdrasil`; la UI solo la provisiona.
- [x] Persistir peers, sesiones de ratchet y mensajes cifrados en almacenamiento nativo accesible por el servicio.
- [x] Portar y vectorizar Ed25519/Curve25519, sealed boxes, X3DH y Double Ratchet en el bridge Go.
- [x] Exponer conversaciones, lectura/comandos de mensajes, estados de entrega y no leídos a la UI sin escritores concurrentes.
- [x] Implementar notificaciones locales desde el servicio, con privacidad de previsualización y deep link al chat.
- [ ] Decidir y validar el autoarranque tras reinicio; si no se implementa, mostrarlo como limitación.
- [ ] Implementar la tabla de ciclo de vida anterior en dispositivo y documentar qué fabricantes la incumplen.
- [ ] Validar en dispositivo force-stop, muerte del proceso UI/servicio, reinicio del servicio y recuperación real de peers; `START_STICKY` no garantiza ejecución perpetua.
- [ ] Obtener revisión de distribución para el tipo `specialUse` y justificar por qué la sesión P2P visible requiere permanencia; la declaración del manifiesto no equivale a aprobación de Google Play.
- [ ] Mostrar al usuario un control explícito para activar/desactivar el servicio y explicar su notificación persistente.
- [ ] Seleccionar el tipo de foreground service y permisos según la función real; no declarar `VpnService`, `connectedDevice` ni otro tipo como atajo para evitar límites del sistema.
- [ ] Si la prueba técnica requiere TUN, limitarlo a la conectividad Yggdrasil aprobada por el usuario; no añadir rutas por defecto ni capturar tráfico no relacionado.
- [ ] Configurar sockets de peering para salir por la red física y evitar routing recursivo.
- [ ] Definir manejo de IPv6, DNS, redes IPv4-only y redes con IPv6 parcial según la arquitectura elegida.
- [ ] Respetar las restricciones de inicio de foreground service, permisos y políticas aplicables a las versiones Android objetivo.
- [ ] Implementar recuperación ante cambios Wi-Fi/datos, pérdida de red, suspensión y reinicio del motor.
- [ ] Distinguir salud del servicio, del motor, peers y transporte.
- [ ] Probar dispositivos AOSP/Pixel y fabricantes con optimizaciones agresivas de batería.
- [ ] Documentar qué comportamiento no se puede garantizar en segundo plano.

#### Criterios de salida

- [x] Con la UI cerrada o swipeada, Desktop puede abrir TCP `50005` y el servicio valida un `HANDSHAKE_REQ` firmado; un frame manipulado se rechaza.
- [x] El servicio persiste el frame validado y emite una notificación local.
- [x] Al reabrir la UI no se duplica el nodo y el listener existente sigue validando frames.
- [x] Al reabrir la UI se lee el estado real y aparecen los mensajes pendientes.
- [ ] Force-stop, muerte del servicio, reinicio del dispositivo y cambios de red tienen comportamiento documentado y visible.
- [ ] El usuario sabe cuándo el nodo y el transporte uPeer están activos y puede detenerlos desde la app o la notificación.
- [ ] La app no declara conectividad solo por tener un servicio activo.
- [ ] El routing y el tráfico normal del dispositivo no relacionado con uPeer/Yggdrasil no se redirigen.
- [ ] Cambios de red producen reconexión medible o un error recuperable y visible.
- [ ] No se promete que el sistema nunca finalizará el proceso ni se usa push como dependencia.

### Fase 7 — Transporte P2P, DHT e interoperabilidad

#### Objetivo

Conectar móviles con nodos actuales y conservar formatos de paquetes.

> La parte DHT de esta fase se ejecuta en el orden bloqueante B0–B9 definido en este mismo documento. No se marca ninguna tarea DHT como completada por tener prototipos aislados: cada una exige vector Desktop y prueba cruzada.

#### Tareas

- [x] Implementar adaptador saliente sobre sockets de overlay: `dialTCP`, `dialUDP`, lectura, escritura y cierre.
- [x] Exponer listener TCP entrante en `50005` y aceptar conexiones de Desktop sobre el netstack, desde el servicio `:yggdrasil`.
- [ ] Mantener los formatos de uPeer transportando paquetes IPv6 desde sockets TCP/UDP normales mediante la pila gVisor de Yggstack, salvo que la prueba de interoperabilidad demuestre una alternativa compatible.
- [ ] Resolver explícitamente TCP entrante, UDP, `-remote-tcp`, `-remote-udp`, SOCKS5 y UDP associate.
- [x] Portar framing, fragmentación, reensamblado y límite de 10 MiB.
- [ ] Portar ACKs, timeouts, reintentos y control de congestión.
- [ ] Portar Kademlia: routing table, buckets, find node/value, exchange, bootstrap, expiración y renovación.
- [ ] Portar bootstrap diversificado y documentar DNS/LAN discovery según capacidades móviles.
- [ ] Desactivar o degradar multicast LAN cuando permisos, OS o red no permitan su uso.
- [ ] Portar location blocks, `DHT_UPDATE` firmado, roaming y rediscovery.
- [ ] Limitar frecuencia de heartbeats, presencia y DHT para batería/datos.
- [x] Validar tipos de paquete, tamaño, firma, rate limiting y límites de memoria.
- [x] Aplicar rate limiting por origen/identidad y política de replay con UUID por conversación, ACK idempotente, `DR_RESET` con `resetId` idempotente y SPK monotónica.
- [ ] Añadir diagnósticos de peers, RTT, bytes, errores y última reconexión sin datos secretos.

#### Criterios de salida

- [ ] Chat de prueba Android↔desktop completa handshake y transporte.
- [ ] Dos móviles pueden descubrirse y comunicarse por la misma red.
- [ ] Cambios de interfaz no duplican ni corrompen mensajes.
- [ ] Paquetes inválidos se rechazan sin consumo descontrolado de CPU/memoria.

### Fase 8 — Diseño y navegación móvil

#### Objetivo

Crear una experiencia táctil nativa en lugar de reducir la interfaz desktop.

#### Tareas

- [x] Definir navegación principal: chats, contactos, llamadas y ajustes; el alcance de grupos está pendiente.
- [x] Diseñar onboarding, importación/creación de identidad y estado de red.
- [ ] Diseñar lista de conversaciones, búsqueda, chat, perfiles, seguridad y ajustes.
- [ ] Adaptar gestos, teclado, safe areas, orientación, tamaños de texto y lector de pantalla.
- [x] Implementar temas y tokens visuales compartibles; no portar widgets Joy UI/MUI a React Native.
- [x] Diseñar estados vacíos, offline, reconexión, mensajes pendientes y fallos de permiso.
- [ ] Añadir navegación profunda desde notificaciones a chat/llamada.
- [ ] Validar uso con una mano, accesibilidad y escalas de fuente.

#### Criterios de salida

- [ ] Flujos principales funcionan sin depender del layout desktop.
- [ ] Estados de red y seguridad se entienden sin términos internos innecesarios.
- [ ] Accesibilidad básica verificada en ambas plataformas objetivo.

### Fase 9 — Identidad social, mensajería y notificaciones

#### Objetivo

Entregar el núcleo de chat directo seguro con persistencia y reintentos.

#### Tareas

- [ ] Añadir contactos por upeer ID/QR, solicitudes entrantes, reputación y bloqueo.
- [ ] Portar chat E2EE y ratchet respetando los vectores de interoperabilidad.
- [ ] Portar respuestas, reacciones, edición, borrado y paginación del historial.
- [ ] Portar estados de envío, entrega y lectura.
- [ ] Implementar outbox durable para envío cuando se recupere conectividad.
- [ ] Portar presencia y escritura con intervalos respetuosos con la batería.
- [ ] Implementar notificaciones locales y acciones compatibles con plataforma.
- [ ] Permitir ocultar previsualizaciones y controlar privacidad en pantalla bloqueada.
- [ ] Sincronizar paquetes recibidos por servicio nativo con base de datos y UI al reanudarse.
- [ ] Probar ACK perdido, mensajes repetidos, offline prolongado, cambios de clave y reintentos.

#### Criterios de salida

- [ ] Mensajes directos E2EE pasan pruebas móviles↔desktop.
- [ ] Reiniciar app no pierde mensajes pendientes ni estado ratchet.
- [ ] Las notificaciones respetan preferencias de privacidad.
- [ ] La presencia no requiere actividad innecesaria en segundo plano.

### Fase 10 — Vault, offline y sincronización entre dispositivos

#### Objetivo

Portar recuperación offline y continuidad multidispositivo sobre B6 y B10. No se reimplementa el self-sync actual de Desktop con mnemonic/SPK compartidas.

#### Tareas

- [ ] Portar vault, renovación de tokens y punteros DHT.
- [ ] Portar recuperación de mensajes y eventos de edición, borrado, reacciones y lectura.
- [ ] Portar recuperación segmentada de adjuntos y su deduplicación.
- [ ] Añadir límites configurables de datos, almacenamiento, batería y replicación.
- [ ] Portar la UI de dispositivos propios, emparejamiento, confianza y revocación sobre el registro firmado de B6; la confianza local no autoriza.
- [ ] Usar `SYNC_ENVELOPE` de B10 para historial y eventos; no generar mensajes nuevos ni reiniciar el ratchet del contacto.
- [ ] Resolver conflicto de orden, duplicados, expiración, eliminación y rotación de claves.
- [ ] Mostrar claramente pendiente, almacenado en vault, recuperado y entregado.
- [ ] Probar largos periodos offline, cambio de IP, reinicio, revocación de un dispositivo offline y recuperación de archivos grandes.

#### Criterios de salida

- [ ] Mensajes y eventos se recuperan móvil↔desktop sin pérdida ni duplicados visibles.
- [ ] La recuperación de adjuntos respeta hashes y cuotas.
- [ ] Reiniciar el servicio no invalida el estado persistido.
- [ ] Un dispositivo revocado no recibe ni descifra mensajes cifrados con el registro posterior al epoch, aunque haya estado offline días.

### Fase 11 — Grupos y roles

#### Objetivo

Portar administración y mensajería grupal con seguridad de miembros.

#### Tareas

- [ ] Crear grupos, invitar, aceptar, salir y gestionar membresía.
- [ ] Portar mensajes grupales E2EE, roles admin/miembro y transferencia de administración.
- [ ] Portar cambio de nombre/avatar y actualización de epoch/claves.
- [ ] Aplicar rotación de claves al entrar/salir/expulsar miembros.
- [ ] Portar fanout con concurrencia limitada, retries y modo offline.
- [ ] Portar invitaciones y controles antiabuso/reputación.
- [ ] Probar grupos mixtos de móvil y desktop, miembro offline y cambio de administrador.

#### Criterios de salida

- [ ] Operaciones de grupo se mantienen consistentes en varios clientes.
- [ ] Un miembro revocado no puede descifrar mensajes posteriores según el protocolo.
- [ ] Fanout y recovery no producen tormentas de red.

### Fase 12 — Transferencias, multimedia y notas de voz

#### Objetivo

Portar adjuntos cifrados usando APIs móviles de selección, almacenamiento y reproducción.

#### Tareas

- [ ] Integrar Document Picker, selección de fotos y captura con permisos mínimos.
- [ ] Portar transferencia chunked, ACK, progreso, cancelación, retries y SHA-256.
- [ ] Portar AES-256-GCM por chunk y sellado de la clave al destinatario.
- [ ] Guardar temporales en almacenamiento privado y limpiar en éxito, cancelación y expiración.
- [ ] Generar miniaturas y previsualizaciones con límites de tamaño/dimensiones.
- [ ] Permitir guardar/exportar a ubicaciones elegidas por el usuario.
- [ ] Implementar grabación y reproducción de notas de voz con permisos y estados claros.
- [ ] Manejar falta de espacio, interrupciones, suspensión y red intermitente.
- [ ] Definir límites por plataforma y cuotas por conversación.
- [ ] Validar MIME por contenido y no solo extensión.

#### Criterios de salida

- [ ] Archivos y voces interoperan con desktop y verifican hash/firma.
- [ ] Interrupción no deja temporales sensibles huérfanos.
- [ ] Los permisos se piden en contexto y se tolera selección limitada.

### Fase 13 — Llamadas de voz/vídeo y compartir pantalla

#### Objetivo

Portar gradualmente llamadas, sustituyendo dependencias específicas de Chromium.

#### Tareas

- [ ] Auditar y clasificar estado real de señalización, transporte, codecs, jitter y cifrado en desktop.
- [ ] Definir captura/codificación nativa para Android e iOS y sus permisos/servicios.
- [ ] Implementar llamada de audio 1:1: oferta, ringing, aceptar/rechazar, timeout y colgar.
- [ ] Añadir mute, ruta de audio, Bluetooth, interrupciones y reconexión.
- [ ] Verificar E2EE, secuencias y compatibilidad de media con cliente desktop.
- [ ] Medir TCP/UDP, jitter, latencia, batería y temperatura.
- [ ] Añadir vídeo 1:1 y ajuste adaptativo solo después de audio estable.
- [ ] Portar grupos mesh pequeños; evaluar SFU/relay, failover y confidencialidad.
- [ ] Implementar compartir pantalla solo donde consentimiento y APIs estén soportados.
- [ ] Integrar mecanismos nativos de llamada si son compatibles con las reglas de distribución.

#### Criterios de salida

- [ ] Audio 1:1 probado durante llamadas prolongadas en dispositivos reales.
- [ ] Interrupciones de sistema y cambios de red tienen resultado predecible.
- [ ] Vídeo/grupos no se consideran completos por la sola presencia de UI.
- [ ] Riesgos de confidencialidad del relay están resueltos o claramente excluidos.

### Fase 14 — Ajustes, seguridad, privacidad y backup

#### Objetivo

Completar capacidades auxiliares y endurecer el manejo de datos móviles.

#### Tareas

- [ ] Portar perfil, avatar, apariencia, notificaciones, privacidad y almacenamiento.
- [ ] Portar huellas de seguridad, alertas TOFU, bloqueados y gestión de contactos.
- [ ] Portar diagnósticos de red/peers con minimización de metadatos.
- [ ] Portar sesiones y dispositivos confiables.
- [ ] Definir backup/restore cifrado y compatibilidad de versiones.
- [ ] Revisar backups del sistema, clipboard, screenshots, archivos recientes y notificaciones.
- [ ] Añadir borrado de cuenta y revisión de eliminación de claves, DB, cachés y blobs.
- [ ] Auditar logs, crash reports, telemetría opt-in y datos identificables.
- [ ] Revisar licencias, SBOM, procedencia de binarios y vulnerabilidades de dependencias.
- [ ] Realizar revisión de seguridad independiente antes de promesas fuertes de privacidad.

#### Criterios de salida

- [ ] Preferencias sensibles se aplican en UI, almacenamiento y notificaciones.
- [ ] Se conoce qué datos sobreviven backup, exportación y desinstalación.
- [ ] La cadena de suministro de Go y módulos nativos está documentada.

### Fase 15 — Pruebas, beta, publicación y mantenimiento

#### Objetivo

Validar el comportamiento real en redes y dispositivos antes de un lanzamiento general.

#### Tareas

- [ ] Crear pruebas unitarias de protocolo, crypto, ratchet, DHT, DB y límites.
- [ ] Crear pruebas de contrato usando los vectores desktop/móvil.
- [ ] Crear integración del bridge Expo, servicio Android aplicable, motor Yggdrasil, transporte y DB.
- [ ] Crear pruebas multiproceso con nodos desktop reales.
- [ ] Crear E2E de onboarding, chat, grupos, archivos, vault y llamadas según alcance.
- [ ] Probar Android físicos de fabricantes distintos y las versiones objetivo.
- [ ] Probar iOS físico y ciclo de vida de extensión si iOS está incluido.
- [ ] Probar cambios Wi-Fi/datos, modo avión, IPv4-only, IPv6 parcial, pérdida de DNS, VPN competidora y reinicio.
- [ ] Probar force-stop, detención del servicio, ahorro de batería, suspensión, actualización y falta de almacenamiento.
- [ ] Hacer fuzzing de paquetes y pruebas de replay/firmas inválidas/DoS local.
- [ ] Crear beta interna y cerrada con builds firmadas y reporte de errores sin datos secretos.
- [ ] Definir métricas opt-in de crash, reconexión, consumo de batería/datos y entrega.
- [ ] Revisar textos de tienda, permisos, política de privacidad y divulgación de la conexión P2P en segundo plano.
- [ ] Lanzar gradualmente con feature flags y plan de rollback.

#### Criterios de salida

- [ ] No hay defectos críticos de seguridad/interoperabilidad abiertos.
- [ ] Las pruebas de hardware cumplen umbrales acordados para reconexión, batería y estabilidad.
- [ ] Los permisos y el uso del servicio de red en segundo plano están divulgados y aprobados para el canal de distribución.
- [ ] Existe soporte, monitoreo y procedimiento de actualización de motor Go.

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación / condición |
|---|---|---|
| El wrapper oficial no expone el transporte necesario | Requiere bridge/fork nativo | Validar en Fase 2 antes de construir UI completa |
| Confundir una VPN/TUN con transporte de aplicación o con garantía de proceso persistente | Captura de tráfico no deseado y promesas engañosas | Mantener separados el nodo overlay, sockets uPeer y ciclo de vida Android; usar TUN solo si una prueba lo demuestra necesario y limitado |
| El binding móvil solo expone paquetes IPv6, no sockets TCP/UDP de overlay | No hay transporte directo equivalente a `-remote-tcp`/`-remote-udp` | Probar wrapper oficial y comparar sockets directos, pila IP de usuario o TUN limitado antes de escoger |
| Routing recursivo de sockets de peering | Pérdida de conectividad | Asegurar que el peering usa la red física y validar rutas en dispositivo |
| Cambiar derivación o serialización | Rompe identidad y firmas con desktop | Vectores de interoperabilidad obligatorios |
| Binding crypto/DB no compatible | Bloqueo o exposición de datos | Spike temprano y revisión de almacenamiento nativo |
| Restricciones de batería por fabricante | Red de fondo irregular | Matriz de dispositivos, preferencias visibles y UX de reconexión |
| Entitlements/App Store no aprobados | No se puede publicar iOS | Validar cuenta y entitlements en Fase 0/2 |
| Binarios Go opacos o no reproducibles | Riesgo de supply chain | Compilar desde fuente, fijar versiones, hashes y SBOM |
| Llamadas sobre transporte TCP | Latencia/jitter altos | Audio primero, medición real y UDP como optimización posterior |
| Compartir mnemonic/SPK entre dispositivos | Revocación imposible y compromiso total de la cuenta | B6: claves por dispositivo, certificado firmado y raíz no copiada; no aceptar `isInternalSync` como autorización |
| Usar la DHT como buzón offline | Pérdida de mensajes y amplificación | B10: DHT solo publica punteros; el payload cifrado por dispositivo vive en vault/custodios con TTL, ACK y deduplicación |
| Sincronizar con un dispositivo apagado | Duplicados o reinicio de ratchet | B10: `SYNC_ENVELOPE` con `eventId` idempotente; cada dispositivo usa su propia sesión con el contacto y una copia sincronizada no ACK ni avanza ese ratchet |

## Registro de decisiones técnicas

| Decisión | Estado inicial | Evidencia requerida |
|---|---|---|
| Plataforma del primer MVP | Pendiente | Alcance de producto y resultados Fase 0 |
| Wrapper móvil de Yggdrasil | Pendiente | Prototipo hardware y revisión de API/commit |
| Transporte TCP/UDP sobre overlay móvil | Viable en Go; binding Android pendiente | Bridge gomobile genera API Java y autolinking Expo descubre el módulo local. Falta AAR (no hay NDK), protocolos reales de uPeer y prueba física Android |
| TUN para Yggdrasil | No asumido | Solo decidir tras comprobar que no hay API de sockets/pila de usuario viable; limitar rutas y consentimiento |
| Binding de criptografía | Pendiente | Vectores cruzados, seguridad y mantenimiento |
| SQLite cifrado | Pendiente | Prueba de DB móvil, migraciones y recuperación |
| Distribución iOS | Pendiente | Entitlements, equipo Apple y aprobación técnica |
| Llamadas móviles | Posterior al MVP | Calidad en hardware y diseño de cifrado media |
| Identidad multidispositivo | B6 y B10; Desktop actual no conforme | Una cuenta, claves por dispositivo, certificado y registro firmados, emparejamiento sin copiar la raíz y revocación. Evidencia: vectores nuevos, migración Desktop/móvil, revocación con dispositivo offline y sync sin duplicados ni reinicio de ratchet |
| Entrega offline entre dispositivos | B10 | DHT solo para punteros; buzón/vault cifrado por dispositivo con TTL, cuotas, ACK y deduplicación. No exigir que ambos dispositivos estén conectados |

## Referencias primarias para la implementación

Las referencias deben volver a comprobarse al iniciar cada fase porque APIs, políticas y versiones pueden cambiar.

- Expo: [Customizing prebuild](https://docs.expo.dev/workflow/customizing/) y [Development builds](https://docs.expo.dev/develop/development-builds/use-development-builds/).
- Android: [Foreground services](https://developer.android.com/develop/background-work/services/foreground-services); consultar la [VPN developer guide](https://developer.android.com/develop/connectivity/vpn) solo si la prueba técnica demuestra que hace falta TUN.
- Yggdrasil Android: [yggdrasil-network/yggdrasil-android](https://github.com/yggdrasil-network/yggdrasil-android).
- Yggdrasil iOS: [yggdrasil-network/yggdrasil-ios](https://github.com/yggdrasil-network/yggdrasil-ios).
- Apple: [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/).
- Contrato de chat y harness locales: [`transport-bridge/harness/README.md`](transport-bridge/harness/README.md).

## Cómo iniciar cada fase

Al empezar una fase:

1. Confirmar su estado con código y pruebas actuales.
2. Si es una fase B, comprobar que la anterior tiene su criterio de salida demostrado; si es de producto, confirmar que B9 está cerrada y, para el Producto 6, que B10 también lo está.
3. Revisar dependencias y criterios de salida de fases previas.
4. Crear tareas pequeñas con archivos, interfaces y pruebas esperadas.
5. Mantener este roadmap como fuente de alcance; registrar cambios de decisión aquí.
5. Al terminar, marcar solo tareas verificadas, añadir evidencia de pruebas y anotar limitaciones o riesgos nuevos.