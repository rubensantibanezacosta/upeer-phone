# Roadmap móvil uPeer con Expo

_Estado: port en curso a 23 de octubre de 2026. Identidad, perfil local, nodo Yggdrasil, transporte saliente/entrante TCP, chat bidireccional con un contacto Desktop ya existente, paquetes DHT firmados, LocationBlock con secuencia persistente, códec/admisión DHT, núcleo Kademlia, store/lookup iterativo y bootstrap/descubrimiento local están verificados; B0–B5 están cerradas localmente. B6 (identidad de cuenta y registro de dispositivos) tiene el núcleo Go completo, clave/certificado de dispositivo, registro local firmado con epoch, guarda de admisión, un trabajador DHT que publica y consulta el registro y el runtime sin raíz; siguen pendientes la prueba de campo de ese trabajador, la revocación desde UI, el handshake sin raíz y la migración de la mensajería de grupos/vault. B7 tiene publicación, lookup iterativo, `DHT_UPDATE` bidireccional con certificado y registro en Android y Desktop, secuencia anti-reinstalación, renovación acotada y admisión de contactos antes de cifrar; la mensajería directa ya usa claves por dispositivo en CHAT, `DR_RESET` y `CHAT_UPDATE`, y sigue pendiente la prueba de roaming real. B8 es el servicio, B9 la interoperabilidad y B10 la sincronización multidispositivo; en B10 el enrolamiento OOB sin raíz ya está implementado en el bridge Go, expuesto por el servicio/módulo Expo y ejecutable desde la pantalla «Vincular este dispositivo», verificado contra Desktop con un vector cruzado._

> Coordinación móvil↔escritorio: el estado conjunto, las dependencias cruzadas y el trabajo abierto de B6/B7 se mantienen en [`../ROADMAP.md`](../ROADMAP.md) (raíz del workspace, fuera de este repositorio).

> Medida de la brecha: «Inventario de paridad con escritorio (23/10/2026)» lista, paquete por paquete y función por función, qué habla ya el móvil, qué queda por portar y qué no se ha probado nunca entre clientes.

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

**Último hito cerrado:** P1 (primer frame entrante Desktop→móvil) y P2 (protocolo headless persistente). El detalle está en [Hitos y prioridad vigente](#hitos-y-prioridad-vigente). El residuo de B6 es el **handshake sin raíz**, que ya no está en el camino crítico: el nodo arranca sin la raíz, las tramas de control se firman por dispositivo y B10 no necesita handshake de contacto porque `SYNC_HELLO` se autentica con certificado de dispositivo. Ese residuo bloquea abrir conversaciones nuevas desde un dispositivo enrolado y espera decisión de diseño (ver «Handshake sin raíz»). B7, B8, B9 y B10 ya no están en fila india: B8 está cableado en el servicio y B7 depende solo de la prueba de roaming. El orden real de trabajo, con el vault antes de la sincronización, está en [Orden de ejecución vigente](#orden-de-ejecución-vigente-25102026).

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
- **B6 — en curso (runtime sin raíz y control firmado por dispositivo hechos; falta el handshake).** `transport-bridge/dht/account_*.go` define certificado de dispositivo firmado por la raíz, solicitud de pairing OOB firmada por el dispositivo, registro de cuenta con `epoch` y revocaciones, admisión certificado→registro, `LocationBlock` firmado por clave de dispositivo con certificado adjunto y publicación/consulta del registro en Kademlia. El binding expone las operaciones desde `dht_signing.go`. En Android, `DeviceIdentityStore` cifra en Keystore una clave Ed25519 de dispositivo y su certificado, `YggdrasilIdentity` lo provisiona al cargar/provisionar la cuenta y `locationBlockJSON` publica el bloque firmado por dispositivo con certificado; el estado se expone por AIDL/TS. El emparejamiento OOB ya tiene UI completa en las dos direcciones (vinculación con QR y pegando códigos, más el autorizador en Perfil). **El runtime en modo dispositivo ya está implementado (26/10/2026):** `NewDeviceOnlyCryptoIdentity` en el bridge construye una identidad con la clave pública de cuenta y sin secreto, y `requireSecretKey` devuelve `ErrRootIdentityUnavailable` en todas las rutas que firman como cuenta —`Sign`, `OpenSealed`, sesiones X3DH, `CreateSignedPreKey`, `BuildAck`, paquetes firmados, handshake accept, `SignCanonicalJSON`, pairing, registro, revocación, bloque de ubicación y certificado de enrolamiento— mientras las rutas de clave de dispositivo (`BuildLinkedChat*`, `BuildLinkedDrReset`, `BuildDeviceLocationBlock`, `BuildSealedEnrollmentRequest`) siguen intactas. En Kotlin, `AccountPublicVault` guarda `{mode, accountUpeerId, accountPublicKey}` cifrado con Keystore, `YggdrasilIdentity` gana `setAccountPublic`/`installEnrollment`/`isDeviceOnly`, carga dual y `mode` en `statusJSON`, y `DeviceIdentityStore` no emite ni renueva certificados sin raíz: instala el que llega del enrolamiento y lo usa mientras no caduque. `deviceEnrollmentFinish` activa el nodo al terminar y en TypeScript `UPeerIdentity` es una unión `root`/`device` con migración del registro guardado, así que la pantalla de vinculación abre la sesión sin la frase. El registro de cuenta no se firma en modo dispositivo, igual que en Desktop: lo publica la raíz y el dispositivo solo lo consume. **Lo que queda para cerrar B6:** el **handshake** sigue exigiendo la raíz. `buildHandshakeRequest` (`device_handshake.go:79`) termina en `buildSignedPacket` y `buildHandshakeAccept` llama a `requireSecretKey`, y `SignedPreKeyStore.currentJSON` crea la SPK de cuenta con `createSignedPreKey`. Las tramas de control ya no dependen de ella (ver abajo), así que un dispositivo enrolado arranca, publica su bloque de ubicación, recibe chat y responde ACK/READ/TYPING/reacciones/borrados, pero **no puede abrir una conversación nueva** hasta que exista un handshake admitido con certificado de dispositivo en el receptor, o una SPK de cuenta firmada por la raíz y transportada desde el enrolamiento. Faltan además los vectores cruzados del registro. La admisión de certificados de contactos ya se aplica en B7.

- **Tramas de control firmadas por dispositivo (26/10/2026).** `BuildLinkedControl(deviceID, accountUpeerID, senderYggAddress, packetJSON)` inyecta `deviceId` y `senderDeviceId`, exige que el `deviceID` derive de la clave que firma y delega en `dht.BuildLinkedSignedPacket`; `BuildLinkedAck` construye el ACK con la misma validación de estado que `BuildAck`. En Kotlin, `SealedControl.frame` y `ReadReceipts.frame` aceptan un `DeviceIdentity?` y `ControlSender` lo pasa cuando el dispositivo está enrolado, así que TYPING, PING/PONG, READ, ACK, reacciones y borrados salen firmados por la clave de dispositivo. En recepción, `PacketSignature.of(packet)` decide por la presencia y validez de `senderDeviceId`: un paquete marcado se verifica con `validateLinkedSignedPacket` y la clave de dispositivo del peer (comprobando antes revocación y que el `deviceId` coincida con el del peer), y solo los de cuenta pasan por `validateSignedPacket` y `rememberPeer`. Antes un ACK firmado por dispositivo se rechazaba por firma, así que un nodo sin raíz podía recibir un chat y no podía confirmarlo. 14 casos nuevos entre Go (`device_control_test.go`) y Kotlin (`PacketSignatureTest`).
- **B7 — en curso (store/lookup iterativo, `DHT_UPDATE` bidireccional y secuencia anti-reinstalación).** El `LocationBlock` firmado por dispositivo se publica bajo la clave DHT del `upeerId`, se valida al recibir `DHT_STORE` y se recupera con lookup iterativo, comprobando certificado y admisión contra el registro de cuenta. El store usa `FIND_NODE` previo, K nodos, ACK y reintento acotado; el lookup incorpora nodos nuevos por rondas α=3, fusiona los bloques de varios dispositivos y cachea solo el resultado verificado. `DHT_UPDATE` se envía a contactos íntimos con todas sus direcciones conocidas, solo al cambiar `dhtSeq` o direcciones, y en recepción exige sobre firmado, certificado, registro vigente y `dhtSeq` estrictamente mayor por `(cuenta, deviceId)`; el último aceptado se persiste en Keystore y no se reenvía. Si falta el estado local de secuencia, el worker recupera el propio bloque DHT y se niega a publicar cuando no puede leerlo o verificarlo. Desktop ya emite bloques con certificado y registro de cuenta, y acepta los de Android. La renovación con límite de saltos y la admisión de contactos antes de cifrar ya están implementadas; `ContactEncryptionAdmission` impide cifrar o reutilizar ciphertext hasta validar registro, ubicación, certificado y revocaciones, con caché de 60 s y política legacy auditada. Siguen pendientes la migración de mensajería Desktop y la prueba de roaming real. El trabajo abierto y su orden están en [`../ROADMAP.md`](../ROADMAP.md).
- **B8 — parcial.** El servicio `:yggdrasil` ya es dueño del nodo, listener, protocolo, ratchets, outbox y persistencia cifrada; falta integrar el trabajador DHT (códec, admisión, núcleo y bootstrap ya disponibles), el `MulticastLock`, la ruta de persistencia de contactos, certificados/epoch y su estado observable.
- **B9 — no iniciada.** No hay validación DHT Desktop↔móvil, fuzzing de paquetes, presupuesto de batería/red para descubrimiento ni interop del registro de dispositivos.
- **B10 — enrolamiento del móvil hecho con escaneo QR y autorizador en el bridge, identidad sin raíz y sincronización pendientes.** El bridge Go ya implementa el protocolo OOB sin raíz que usa Desktop: decodifica la invitación, firma la solicitud del dispositivo sobre el payload canónico y la sella con el secreto de un solo uso, y abre y verifica el certificado emitido por la cuenta antes de instalarlo (`ParseEnrollmentInvite`, `CryptoIdentity.BuildEnrollmentRequest`, `OpenEnrollmentCertificate`). También cubre el lado autorizador desde el bridge: `createEnrollmentInvite`, `openEnrollmentRequest` e `issueEnrollmentCertificate` generan la invitación con `inviteId` derivado y TTL acotado, abren la solicitud comprobando cuenta y ventana de 10 min, y firman y sellan el certificado; el vector de `transport-bridge/testdata/enrollment_vectors.json` verifica ambas direcciones, incluida una firma de certificado idéntica byte a byte a la de Desktop. El vector se verifica en ambos clientes. `DeviceEnrollment` guarda el estado en Keystore (solicitud pendiente + certificado instalado), el AIDL y el módulo Expo exponen `deviceEnrollmentBegin`/`…RequestJSON`/`…Finish`/`…Cancel`/`…StatusJSON`, y la pantalla única de recuperación del acceso ejecuta el flujo completo pegando códigos o escaneando con la cámara (`expo-camera`, `CameraView` con `barcodeTypes: ['qr']`) los QR que muestra el escritorio: invitación y certificado de respuesta. El reparto es asimétrico por hardware — el único dispositivo con cámara es el móvil, así que se escanea siempre escritorio → móvil y la solicitud del teléfono se copia. El autorizador móvil está cerrado: `DeviceEnrollmentAuthorizer` guarda invitaciones cifradas, abre la solicitud del escritorio, emite el certificado y lo registra, y Perfil ofrece «Dispositivos de la cuenta» con hoja de invitación QR. Falta el runtime en modo dispositivo (la app sigue necesitando la raíz para operar), la clave de cuenta pública sin mnemonic y toda la sincronización online/offline, historial y entrega diferida entre dispositivos autorizados.
- **B11 — transporte local sin internet: no iniciada y bloqueada a propósito.** Sin Yggdrasil no hay nodo, y cada paquete firmado exige `senderYggAddress` IPv6, así que el móvil no puede hablar con el dispositivo de al lado. Falta Bluetooth RFCOMM, LAN local, descubrimiento propio y admisión por dispositivo en vez de por IP. **No se implementa sin discutir el diseño con el usuario antes**: ver el aviso de «B11 — Transporte local sin internet».
- **Fases de producto 4–11 — no iniciadas como paridad.** El chat directo con un contacto ya conocido funciona en ambos sentidos y está expuesto en la UI, pero faltan grupos, archivos, llamadas, vault y notificaciones avanzadas. El Producto 6 (vault/multidispositivo) no empieza hasta B10. El Producto 11 (blogs P2P) es lo último de la cola y no se empieza con la mensajería sin cerrar: no es un transporte, es un producto nuevo que necesita firma de contenido y entrega ya probada. El detalle medido por tipo de paquete y por función de escritorio está en «Inventario de paridad con escritorio»: de los más de 60 tipos de paquete del protocolo (41 en `network/types.ts`, 14 `CALL_*`, 4 `GROUP_*` y los `FILE_*` que solo aparecen en `SEALED_TYPES`), el móvil solo habla hoy `HANDSHAKE_*`, `CHAT`, `ACK`, `READ`, `TYPING`, `CHAT_REACTION`, `CHAT_DELETE`, `CHAT_UPDATE`, `DR_RESET` y los `DHT_*`.

### Disposición del trabajo local no integrado

| Archivo | Estado | Acción antes de continuar |
|---|---|---|
| `transport-bridge/dht_signing.go` | Integrado; adaptador gomobile | — |
| `transport-bridge/location_block.go` | Integrado; adaptador gomobile | — |
| `transport-bridge/dht_signing_test.go` | Prueba del adaptador | — |
| `DhtTypes.kt`, `DhtRoutingTable.kt`, `DhtValueStore.kt` | Retirados; reemplazados en B4 | Implementación vigente en `app/upeer/transport/dht/kademlia/`, no en la raíz |

Ningún prototipo cuenta como fase completada hasta que tenga vector Desktop, binding y prueba cruzada.

## Orden por fases bloqueantes

Cada fase B solo puede empezar cuando la anterior tenga su criterio de salida demostrado. Las fases de producto (4–10) no se adelantan para tapar huecos de red: dependen de B9; el Producto 6 depende además de B10. B11 es la excepción de orden: puede prototiparse en paralelo porque solo toca el transporte, pero no se cierra antes de B9, cuando el contrato de paquetes ya admite un origen sin dirección Yggdrasil.

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
| B11 | Modo «solo local» | Transporte local sin internet: Bluetooth RFCOMM, LAN y admisión por dispositivo | Bloqueada a propósito: requiere decisión del usuario |

## Orden de ejecución vigente (25/10/2026)

Las fases B0–B11 siguen siendo las unidades de trabajo, pero su numeración **no es el orden de ejecución**. Este es el orden derivado de bloqueos verificados en código, no de la comodidad de tener un módulo caliente:

| Orden | Trabajo | Bloquea a | Por qué va aquí |
|---|---|---|---|
| 1 | **Runtime en modo dispositivo** (residuo de B6) | ~~B10~~ cerrado el 26/10/2026 | El nodo arranca sin raíz, el enrolamiento activa el certificado y las **tramas de control ya se firman por dispositivo** (`BuildLinkedControl`/`BuildLinkedAck`). Lo que queda de B6 es el **handshake sin raíz**, y **no bloquea B10**: `SYNC_HELLO` de escritorio se autentica con el certificado de dispositivo y su SPK firmada por la clave de dispositivo (`network/sync/auth.ts:36-60`), sin handshake de contacto. Ese residuo bloquea la paridad real de B6/B9 (abrir conversaciones nuevas desde un dispositivo enrolado) y necesita decisión de diseño: ver «Handshake sin raíz». |
| 2 | **`VAULT_*` y entrega diferida** (en curso: emisor, custodio, buzón, custodia local, puntero en DHT, `VAULT_RENEW` y estados cableados; faltan la prueba cruzada con escritorio como custodio real y la decisión sobre cuotas por reputación) | estados `vaulted`, cierre de UI, fin del `failed` permanente | **No depende de `SYNC_*`**: `vault/manager.ts` de escritorio solo usa Kademlia, contactos, reputación y `sendSecurePacket`. Las primitivas ya están en el móvil: `KademliaNode.storeValue` (85), `KademliaIterative.storeValue` (52), `KademliaRoutingTable.findClosestContacts` (25) y `refreshRegistry`. |
| 3 | **`SYNC_*`: transporte y aplicador** (cierre de B10; en curso: `SYNC_HELLO`, `SYNC_ACK` y `SYNC_ENVELOPE` recibidos, autenticados y registrados). **Bloqueado por el descubrimiento de dispositivos propios**: sin él no sale ni un `SYNC_HELLO` y la prueba cruzada no arranca. | estados `synced`, eventos de perfil y grupo | Las piezas puras están hechas y probadas (46 casos) y el receptor ya está cableado. Lo que falta es el ratchet `sync:<deviceId>` para `SYNC_ENVELOPE` y el historial, y el aplicador de los nueve tipos. `SYNC_HELLO` se autentica por certificado de dispositivo, así que **no necesita la raíz**: el residuo del handshake sin raíz no bloquea este paso, solo abrir conversaciones nuevas desde un dispositivo enrolado. |
| 4 | **`FILE_*`** | notas de voz, adjuntos en grupo, paridad de adjuntos | La recuperación offline de adjuntos se apoya en los shards del vault, que ya estará hecho. |
| 5 | **`GROUP_*`** | llamada de grupo | Necesita sender keys, fanout por dispositivo y adjuntos, así que va después de `FILE_*`. |
| 6 | **`CALL_*`** | — | El bloque más grande y el único con requisitos de hardware y red en tiempo real. |
| 7 | **Cierre de UI** (ajustes reales, notificaciones, revocación) | — | Solo tiene sentido cuando existan los estados que debe pintar. Incluye sacar «Desconectar» de la notificación persistente y llevar a Ajustes → Conexión el interruptor de segundo plano, que al apagarse debe impedir que el proceso `:yggdrasil` sobreviva al cierre de la app. Cuelga también de aquí la **emisión de presencia del móvil** (ver «Presencia desde el móvil»): no bloquea ningún paso, pero es la razón de que escritorio no vea al móvil «En línea» mientras se usa. |
| 8 | **B12 (blogs P2P)** | — | Producto nuevo, no transporte. |

B9 no es un paso sino una **puerta**: la matriz de interoperabilidad, fuzzing y presupuesto de batería se ejecuta al cerrar cada uno de los pasos anteriores sobre lo que ese paso toque, no al final de todos. La prueba real Desktop↔móvil del 25/10/2026 ya cubrió la parte de chat, recibos, reacciones, edición, respuesta, borrado y presencia.

Dos correcciones respecto a lo que decía este roadmap antes:

- **El vault no va después de la sincronización.** La dependencia «`VAULT_*` depende de `SYNC_*`» era falsa y venía de confundir el buzón por dispositivo del *destinatario* con la sincronización entre dispositivos *propios*. Son protocolos separados que solo comparten el almacén de mensajes y, por tanto, la regla de deduplicación por `messageId`.
- **B8 no bloquea nada.** El trabajador DHT está cableado en el servicio; lo que queda de B8 es presupuesto de batería y matriz de pruebas, no integración.

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

**Estado (25/10/2026): implementado; faltan presupuesto de batería y matriz de pruebas.** El roadmap daba el trabajador DHT por pendiente y no lo está: `YggdrasilDhtRuntime` (297 líneas) vive en el módulo, lo arranca y lo para `YggdrasilTransportService` (`start`/`stop` en líneas 65, 69, 89, 93, 148, 151, 341), procesa frames entrantes (`handleFrame`, línea 28), reacciona al cambio de red (`onNetworkChanged`, línea 154, con `bootstrap.onNetworkChanged()` y republicación forzada), ejecuta mantenimiento periódico con republicación de registro y ubicación (líneas 252-259), y publica estado por AIDL (`statusJSON` con `bootstrapped`, `published` y `locationPublished`). El servicio sobrevive al cierre de la UI (`onTaskRemoved`, línea 379). Lo que queda es el presupuesto de batería y la matriz de pruebas, no el cableado.

**Depende de:** B7.

- [x] Ejecutar el trabajador DHT dentro del servicio, no en React; sobrevivir a `onTaskRemoved` y al cierre de la UI.
- [x] Persistir contactos verificados, `dhtSeq`, `deviceId`, certificado de dispositivo, epoch, revocados y bloques propios con límites y migración (`KademliaContactFileStore`, `DhtLocationStore`, `DeviceIdentityStore`, `AccountRegistryStore`).
- [~] Programar refresco de buckets y republicación con presupuesto de batería/datos; suspender cuando el usuario desactive la conexión. Refresco y republicación hechos (`maintenance`, cambio de red); **falta el presupuesto medido y el respeto a Doze**.
- [x] Exponer estado observable: `nodeId`, contactos, bootstrapped, último bootstrap, valores almacenados, paquetes enviados/recibidos y último error, sin secretos.
- [~] Probar UI cerrada, reinicio del servicio, cambio de red y parada manual. UI cerrada verificada en la prueba real de interoperabilidad; reinicio, cambio de red y parada manual quedan pendientes.

**Salida:** con la UI cerrada el DHT sigue operativo, se recupera tras reinicio, conserva certificado/epoch/revocados y la UI muestra un estado que distingue “servicio activo” de “DHT saludable”. El DHT ya no bloquea nada: ni el vault ni la sincronización.

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

**Estado (23/10/2026): enrolamiento completo en las dos direcciones; identidad sin raíz y sincronización pendientes.** `DeviceEnrollment` implementa el lado del dispositivo nuevo del protocolo OOB: decodifica la invitación con el bridge, firma y sella la solicitud, abre el certificado emitido por la cuenta, comprueba que `accountUpeerId` y `deviceId` corresponden a esta clave y persiste solicitud pendiente y certificado en Keystore. El servicio `:yggdrasil` lo expone por AIDL y el módulo Expo por `deviceEnrollmentBegin`, `deviceEnrollmentRequestJSON`, `deviceEnrollmentFinish`, `deviceEnrollmentCancel` y `deviceEnrollmentStatusJSON`, con los códigos convertidos a base64url en TypeScript. `DeviceEnrollmentAuthorizer` cubre la dirección contraria: invitaciones pendientes cifradas en Keystore, apertura de la solicitud del escritorio probando cada invitación viva, emisión del certificado sobre `epoch + 1`, reverificación del certificado y alta en `AccountRegistryStore`, expuesto como `enrollmentAuthorizerCreateInvite`, `AcceptRequest`, `Complete`, `StatusJSON`, `Cancel` y `CancelAll`. La pantalla «Vincular este dispositivo» del acceso ejecuta los tres pasos del lado nuevo, con pegado de códigos y escaneo QR de la invitación y del certificado con la cámara del teléfono; en Perfil, «Dispositivos de la cuenta» lista el registro firmado marcando el dispositivo en uso y abre el autorizador en una hoja inferior con invitación QR, escaneo de la solicitud y pegado manual. La UI se alineó con escritorio: Contactos pasa a cabecera de sección, búsqueda y lista de tarjetas con alta en hoja inferior (`ContactFormSheet`, identidad + alias + `Escanear QR del contacto`), y Chats cambia el botón flotante por la acción de cabecera que abre `NewConversationList`. Faltan el runtime en modo dispositivo (hoy la app sigue exigiendo la raíz para arrancar el nodo), la sincronización multi-dispositivo en el móvil, los estados `pending`/`synced`/`vaulted`/`delivered`/`failed` en la UI y la prueba real Desktop↔móvil. El resto de huecos de interfaz está en «Pendiente en la UI móvil».

**Depende de:** B9. **Bloquea a:** Producto 6.

- [ ] Crear un canal pairwise entre dispositivos con X3DH/Double Ratchet usando sus claves de dispositivo y certificados; separado de las sesiones con contactos. No reutilizar el ratchet del contacto ni la SPK de otro dispositivo.
- [ ] Definir `SYNC_ENVELOPE` firmado y cifrado con `deviceId`, `epoch`, secuencia, tipo y `eventId`/`messageId`; idempotencia por UUID. Una copia sincronizada conserva el ID y el contenido original y no crea un mensaje nuevo ni reinicia el ratchet del contacto.

**Base hecha (25/10/2026).** El registro de eventos y la verificación de origen ya existen en `:upeer-transport`, como piezas puras con pruebas JVM (46 casos en `SyncEventLogTest`, `SyncDevicePeersTest`, `SyncEventOriginTest` y `SyncPacketsTest`): `SyncEventKinds` fija los nueve tipos, `SyncSequence` reproduce la secuencia monótona de escritorio (`max(última + 1, ahora × 1000)`), `SyncEventLog` persiste el evento en el bucket `sync` del estado del protocolo con deduplicación por `eventId`, poda a 2000 entradas, ACK por dispositivo y contador de pendientes, `SyncHello` valida el binding del dispositivo (certificado, `deviceId` derivado y SPK firmada) con tolerancia de reloj, `SyncDevicePeers` guarda los dispositivos propios con dirección y elige los destinos de relay excluyendo emisor y origen, y `SyncEventOrigin` firma y verifica el payload canónico del evento con `signCanonicalJSON` y `verifyDeviceCertificate`. `SyncPackets` cierra el formato de paquete: `SYNC_HELLO` sin destinatario, `SYNC_ACK`, metadatos de `SYNC_ENVELOPE` con `epoch` y origen, metadatos de historial por `requestId`, y `SyncPlainEvent`/`matchesEnvelope` replicando la validación y la comprobación de sobre contra lo descifrado de `handlers.ts`. Falta el transporte: `SYNC_HELLO` entrante, `SYNC_ENVELOPE` con el ratchet `sync:<deviceId>`, historial por chunks, relay efectivo y el aplicador de los nueve tipos sobre el almacén local. **Condición de cierre:** B10 no se cierra antes del runtime en modo dispositivo, porque su criterio de salida prohíbe copiar la raíz y hoy el nodo móvil no arranca sin ella. Por eso la sincronización es el paso 3 del orden vigente y no el siguiente inmediato: primero se quita la raíz del móvil, después se sincroniza sobre claves de dispositivo, y en medio entra el vault, que no depende de esto.
**Receptor cableado (26/10/2026).** Los `SYNC_*` ya no mueren en «unsupported packet type»: el móvil los intercepta al principio de `handleSignedPacket`, antes de la ruta de contactos, y los autentica **como dispositivos propios**, replicando `earlyPacketRouter.ts`:

- `SyncSelfAuth` exige que `senderUpeerId` sea la propia cuenta, que `senderDeviceCertificate` verifique contra ella en un instante de referencia que nunca es futuro (`max(ahora, issuedAt)`), que su `deviceId` coincida con `senderDeviceId` y que el dispositivo no esté revocado. La firma la valida **el puente** con `validateLinkedSignedPacket` contra `devicePublicKey` del certificado, que es exactamente lo que hace `verifySelfSyncSignature` de escritorio: misma cobertura (`senderUpeerId`, `senderYggAddress`, `isInternalSync`), misma exclusión de `signature`/`contactCache`/`renewalToken`. 11 casos unitarios, incluido el verificador que lanza en vez de devolver `false`.
- `SyncReplies` registra el dispositivo en `SyncDevicePeers` y **responde al `SYNC_HELLO` inicial con `ack = true`**, que es como escritorio evita el bucle de saludos; `SYNC_ACK` avanza el ACK por dispositivo. Los otros tres tipos se reconocen, se limitan por origen y se registran con el motivo por el que todavía no se aplican, en vez de lanzar excepción.
- **`SYNC_ENVELOPE` ya se descifra y se registra.** `SyncChannel` usa las primitivas que el puente ya expone y que escritorio usa en su mismo orden: `startOutboundSession`/`acceptInboundSession` con **la clave de dispositivo, no la de cuenta**, `ratchetEncrypt`/`ratchetDecrypt`, y la sesión guardada bajo `sync:<deviceId>` (clave de mapa aparte, así que una copia sincronizada no puede tocar el ratchet del contacto ni provocar un `DR_RESET`). `SyncEnvelope` decide qué se acepta antes de escribir nada: destinatario igual al dispositivo propio, metadatos del sobre idénticos a lo descifrado, **evento propio reflejado por otro dispositivo rechazado**, evento relayado sin firma de origen válida rechazado, y firma presente pero no verificable rechazada. Se registra con `SyncEventLog` y se responde `SYNC_ACK`, también en los rechazos, para que el emisor no reintente para siempre. 11 casos unitarios nuevos.
- `SYNC_HISTORY_REQ` y `SYNC_HISTORY_RES` siguen pendientes, y el **aplicador de los nueve tipos** también: hoy el evento se registra en el log pero todavía no se proyecta sobre mensajes, reacciones, borrados ni perfil.
- **Un fallo de la tanda anterior quedó corregido de paso:** `VAULT_RENEW` no estaba en `VAULT_TYPES`, así que el custodio nunca lo veía y el paquete acababa en `unsupported packet type`. La renovación estaba escrita y probada pero desconectada.
- `ProtocolEgress` unifica el `dialTCP` + `writeFrame` + cierre que vault y sync duplicaban, y deja `YggdrasilProtocol` por debajo de donde estaba pese a añadir la rama nueva.

**⛔ Bloqueo real de B10 encontrado el 26/10/2026: los dispositivos de una misma cuenta no se encuentran.** Con el receptor cableado, la prueba en dispositivo no produjo ni un solo `SYNC_HELLO` en seis minutos. La investigación dio tres hechos verificados, y el tercero es el que bloquea:

1. **Escritorio no manda el saludo porque no tiene a quién.** `window.upeer.getSyncStatus()` sobre la app en marcha devuelve `{"state":"disabled","peers":[]}`. Ese `disabled` no es un interruptor: `storage/sync/status.ts` lo devuelve cuando `listSyncPeers()` está vacío, y la lista solo se llena al recibir o enviar un `SYNC_HELLO`.
2. **El descubrimiento de dispositivos propios de escritorio no puede funcionar con el móvil.** `getSelfAddresses` (`chatSupport.ts:138`) recorre `kademlia.findClosestContacts(myId, 20)` y **solo conserva los contactos cuyo `upeerId` es el propio**. Pero el móvil nunca llega a la tabla de rutas del escritorio: `handleDhtUpdate` (`legacyHandlers.ts:80`) exige que el emisor sea **un contacto** (`getContactByUpeerId`), y el móvil es la misma cuenta, no un contacto. `handleDhtExchange` tiene la misma guarda. Y aunque llegara, `KademliaNode.probeContacts` del móvil (`KademliaNode.kt:124`) **descarta explícitamente los candidatos con su propio `upeerId`**, así que el filtro simétrico del escritorio tampoco se cumple.
3. **El bloque de ubicación es uno por cuenta, no uno por dispositivo.** `DhtLocationWorker.publish` guarda con `store(account, block, account)`, y `storeLocationBlock` de escritorio usa `toKademliaId(upeerId)`: la misma clave para todos los dispositivos de la cuenta. El último que publica pisa al anterior, y `select` devuelve **un** dispositivo. Por eso no hay forma de enumerar las direcciones de los dispositivos propios ni por DHT ni por tabla de rutas.

Consecuencia: la entrega sincronizada entre dispositivos propios no puede arrancar sola. El `SYNC_HELLO` entrante desde escritorio sí se autenticaría si llegara, pero nadie lo envía. Hace falta un descubrimiento propio antes de la prueba cruzada.

**Primera mitad hecha (26/10/2026): clave de encuentro por dispositivo.** El móvil ya publica y resuelve su propio encuentro, sin tocar nada de escritorio:

- `SyncRendezvousKey` deriva `SHA-256("sync-device-v1:<cuenta>:<deviceId>")` truncado a 20 bytes, con la misma forma que el puntero del vault: una clave por dispositivo, así que **el último que publica ya no pisa a los demás**, que es el defecto de la clave de ubicación compartida.
- `SyncRendezvousRecord` publica `{accountUpeerId, deviceId, address, certificate, publishedAt}` y lo valida al leer: cuenta esperada, certificado cuyo `deviceId` coincide, caducidad de 7 días y tolerancia de reloj de 10 minutos hacia el futuro para no rechazar por un desfase. 12 casos unitarios.
- `SyncDiscovery` publica el registro al arrancar y luego **enumera los `deviceId` de la cuenta desde el registro de cuenta** y lee la clave de cada uno, en vez de fiarse de la tabla de rutas. Los dispositivos encontrados reciben un `SYNC_HELLO` con `ack = false`.

**Dos bloqueos de entorno medidos en la misma prueba, y el segundo es real:**

1. **La DHT del móvil no publica nada, y la causa está localizada y es de diseño.** `storePointer`, `readPointer` y `publishRegistry` empiezan todos con `if (!bootstrap.isBootstrapped() || !admitted()) return`, y `bootstrapped` significa `verifiedContacts.size >= BOOTSTRAP_MIN_NODES`, que vale **10** tanto en `KademliaConfig` (móvil) como en `types.ts` (escritorio). El móvil no llega a 10 verificados porque no tiene semillas: `SEED_NODES` está vacío en escritorio, `seednodes.json` no existe, y `KademliaSeedLoader` solo aporta contactos ya persistidos y DNS TXT. Los frames `DHT_UPDATE`/`DHT_EXCHANGE` que sí recibe no cuentan como verificados; solo lo hacen las pruebas de `probeContacts`, y las que intenta contra nodos de la tabla de rutas caducan (`No se pudo enviar un paquete DHT a 202:fdcd:…: operation timed out`). Consecuencia: en el móvil **el registro de cuenta, la ubicación, el puntero del vault y el encuentro de sincronización están todos muertos**, no solo el de sync. Es un bloqueo anterior a B10 y afecta a cualquier despliegue de dos nodos: hace falta decidir si se baja el umbral, si se cuentan como verificados los contactos que responden a nuestras consultas DHT, o si se admite publicar en modo degradado con los contactos alcanzables.
2. **El móvil no es un dispositivo de la cuenta de escritorio.** `window.upeer.getAuthorizedDevices()` en escritorio devuelve `{"devices":[],"epoch":null}` y `getMyDevices()` devuelve `{}`; el registro de cuenta que el móvil tiene en local no anuncia otros dispositivos, y el móvil hizo `HANDSHAKE_REQ` contra `da4163…` (la cuenta de escritorio) como si fuera **un contacto más**. Es decir: las dos instalaciones son **cuentas distintas**, no dos dispositivos de la misma cuenta. Sin enrolar el móvil en la cuenta de escritorio no hay B10 que probar: `sync` entre cuentas distintas no existe, y el `SYNC_HELLO` que llegue se autentica contra el propio upeerId.

Salida propuesta, pendiente de decidir con el usuario porque cambia el protocolo de descubrimiento:

- **A — Clave de encuentro por dispositivo en la DHT.** Cada dispositivo publica su propia clave derivada de forma determinista (`SHA-256("sync-device:<cuenta>:<deviceId>")`, con la misma forma que el puntero del vault) con su dirección y certificado, y cada dispositivo resuelve la lista de `deviceId` de su cuenta desde el **registro de cuenta** —que ya se publica y ya se refresca— leyendo luego la clave de cada uno. Ventaja: no depende de la tabla de rutas, no colisiona entre dispositivos y reutiliza maquinaria que ya existe. Coste: una publicación y una lectura por dispositivo, y el registro hay que tratarlo como fuente de la lista.
- **B — Aceptar bloques de ubicación de la propia cuenta.** Relajar `handleDhtUpdate` y `handleDhtExchange` para admitir emisores cuyo `upeerId` sea el propio, verificando el bloque **contra el registro de cuenta** (`VerifyDeviceLocationBlock`, que ya usa el móvil) en vez de contra la tabla de contactos. Ventaja: no añade claves nuevas. Coste: sigue habiendo **un solo** bloque por cuenta en la DHT, así que el último dispositivo que publique oculta a los demás; habría que cambiar también la clave.

**A es la única que sostiene más de dos dispositivos**, que es el caso que B10 tiene que probar; B arregla el caso de dos y deja el tercero fuera. Recomendación: A, con la clave del vault como plantilla.

**Piezas puras hechas (26/10/2026).** El lado emisor y el lado custodio del vault ya existen como código puro y probado, sin cablear al servicio:

- `vault/VaultCustody.kt`: elige los custodios de un paquete replicado con una función determinista — descarta al emisor y al destinatario, exige `upeerId` hexadecimal de 32 bytes y dirección no vacía, deduplica quedándose con el `lastSeen` más reciente, ordena por `lastSeen` descendente, desempata por `upeerId` y corta en el factor. **El factor es fijo en 8**, el valor de emergencia que ya usa `VaultManager` de escritorio cuando falla la consulta de reputación, y no 12: el móvil no tiene `vouchScore` y copiar el cubo real (0–69 → 12) repartiría los mensajes entre doce custodios sin ninguna señal de confianza que lo justifique. 11 casos.
- `vault/VaultStoreEnvelope.kt`: construye el `VAULT_STORE` compatible con escritorio — `payloadHash` = SHA-256 de los bytes del paquete (no del `JSON.stringify` recomputado, para que el receptor recupere los bytes exactos), `data` en hex, `priority` 1/2/3 (`CHAT` / resto / `CHAT_CLEAR_ALL`·`CHAT_DELETE`), `expiresAt` con TTL clampado a 60 días y `recipientDeviceId` opcional. 11 casos.
- `vault/VaultHandover.kt`: entrega a los custodios deduplicando direcciones y cuenta aceptados; `stored = accepted > 0`, que es la condición para marcar `vaulted`. 7 casos.
- `vault/VaultStoreRequest.kt`: la entrada del custodio. Valida contra los mismos límites que `validationVaultGroups.ts` (hash ≤ 200 hex, datos ≤ 150 000 hex, prioridad 1–3), acepta el destinatario comodín `*`, **usa el remitente autenticado y nunca el campo `senderSid` del payload** (el fallo que en escritorio permitía no contabilizar cuota), clampa la caducidad y rechaza lo que ya nació caducado. 16 casos.
- `vault/VaultQueryDelivery.kt`: `VAULT_QUERY` y `VAULT_DELIVERY` con paginación de 120 y `hasMore`, filtrado por destinatario, comodín, dispositivo y caducidad, consulta de un hash concreto y **rechazo si el solicitante no es el remitente autenticado**. No envía entregas vacías. 16 casos.
- `vault/VaultAcknowledgement.kt`: `VAULT_ACK` con el tope de 200 hashes de escritorio y la comprobación de que la entrada que se libera pertenece al destinatario y al dispositivo que acusa recibo. 13 casos.

**Cableado hecho (26/10/2026).** Ya no son piezas sueltas:

- `vault/VaultMailboxStore.kt` persiste el buzón con `KeystoreStore` (AES-GCM con clave del Keystore de Android) y `VaultMailbox` le pone ids, deduplicación por hash, purga por caducidad, tope de 2 000 entradas y **cuota de 50 MB por remitente**, que es el tramo más bajo de escritorio. El estado se sanea al cargar: una entrada sin hash o sin remitente se descarta y el contador se recoloca por encima del id máximo, así que un documento corrupto no rompe el arranque.
- `VaultReplies.kt` es el custodio. Verifica la firma como el resto (`PacketSignature`: cuenta o dispositivo, con revocación y `deviceId` del peer), aplica el rate limit nuevo de `VAULT_*`, y atiende `VAULT_STORE` (almacenar y acusar), `VAULT_QUERY` (entregar página o un hash concreto), `VAULT_ACK` (liberar **solo** entradas del que acusa, con su `deviceId`) y `VAULT_DELIVERY` (aplicar). **Nunca responde al `sourceAddress` de la conexión si el peer tiene dirección registrada**, y si no la tiene exige que la de origen sea una IPv6 de Yggdrasil: sin eso, un paquete podía convertir al nodo en emisor hacia una IP arbitraria.
- La entrega se aplica **reprocesando cada entrada por el mismo camino que un paquete entrante** (`handleFrame`), no por una ruta paralela: así hereda verificación de firma, anti-replay por `messageId`, descifrado del ratchet y deduplicación. Una entrada que no se puede aplicar no se borra del buzón ni se ACK.
- `vault/VaultChannel.kt` firma y sella lo saliente: `buildLinkedControl` cuando hay dispositivo (o `buildSignedPacket` con raíz) y `seal` al custodio, igual que escritorio, que incluye los `VAULT_*` en `SEALED_TYPES`.
- **El outbox deja de rendirse.** Antes, 8 reenvíos sin ACK llevaban a `failed` permanente; ahora, en ese punto exacto, `YggdrasilOutbox.escrow` construye el sobre con el paquete ya cifrado que tenía guardado, elige custodios con `VaultCustody` (factor 8, sin el emisor ni el destinatario) y, **solo si al menos uno acepta**, borra la entrada del outbox y marca el mensaje `vaulted`. Si nadie acepta, el comportamiento anterior se conserva. `CHAT_UPDATE` no se custodia: es una edición de algo que ya existía.
- **Consulta al recuperar conexión, sin puntero todavía.** `queryVaults` pregunta a todos los contactos al arrancar y `queryVault` al aceptar un handshake. Es la versión barata y correcta mientras no haya puntero: no se sabe qué ocho custodios tienen copia, así que se pregunta a todos; cuesta un paquete por contacto y es idempotente porque la entrega se deduplica al aplicarse.
- **Estado nuevo en la UI.** `vaulted` entra en el tipo de mensaje de TypeScript y en la burbuja (etiqueta accesible «Guardado en el vault», con el mismo check simple que usa escritorio para ese estado).

**Puntero en la DHT (26/10/2026).** Deja de preguntarse a todos los contactos:

- `vault/VaultPointerKey.kt` reproduce **exactamente** la derivación de escritorio (`network/dht/kademlia/store.ts`): `SHA-256("vault-ptr-v2:<sid>[:device:<id>]:<YYYY-MM-DD UTC>")` truncado a 20 bytes, en hex. La sal diaria existe para que un observador de la DHT no pueda reconstruir el grafo social preguntando por uPeer IDs conocidos. Verificado byte a byte contra la fórmula de escritorio en `VaultPointerKeyTest`, y comprobado que la distancia DHT coincide: `toKademliaId` de escritorio hashea los **bytes decodificados** del hex y `KademliaId.fromLookupHex` del móvil hace lo mismo, así que ambos publican y buscan en los mismos nodos.
- `vault/VaultPointers.kt` construye y valida el valor: `custodians` (incluido uno mismo, como escritorio) y `custodianEndpoints` con `upeerId`+`address`, más `recipientDeviceId` opcional y `updatedAt`. Deduplica custodios y direcciones, acota a 16 y 16, y descarta entradas con uPeer ID inválido. **La lectura tolera más que la escritura**: acepta `JSONObject` o cadena, ignora endpoints malformados en vez de tirar el puntero entero y normaliza un `updatedAt` ausente o futuro.
- **La lectura prueba hoy y ayer.** Escritorio solo lee la clave del día UTC, así que a las 00:00 se queda sin ver sus propios punteros hasta republicar; el móvil consulta la clave de hoy y la de ayer para no repetir ese hueco.
- `vault/VaultDirectory.kt` une las dos mitades: publica tras custodiar y resuelve antes de sondear. Quien llama aporta las dos funciones (`storePointer`/`readPointer` en `YggdrasilDhtRuntime`, que ya rechazan publicar si el bootstrap no está listo o el dispositivo no está admitido).
- **La custodia publica y la entrega resuelve.** `YggdrasilOutbox.escrow` publica el puntero con los custodios que **aceptaron** el sobre; si la publicación falla el mensaje queda igualmente `vaulted` y se avisa en el log, porque el mensaje ya está a salvo y el sondeo sigue existiendo como red de seguridad. Al arrancar, `queryVaults` resuelve el puntero y consulta **solo esos custodios**; el barrido a todos los contactos se conserva como respaldo cuando no hay puntero.

**Dos fallos corregidos al cablear el puntero (26/10/2026), ambos de los que no dan error visible:**

- **El puntero se publicaba en la clave del emisor, no en la del destinatario.** `VaultDirectory.publish` derivaba la clave con `identity.current()`, así que el mensaje quedaba a salvo pero el receptor nunca encontraba su buzón: al reconectar consultaba una clave que nadie había escrito y el barrido de respaldo a todos los contactos era lo único que lo salvaba. Ahora `VaultPointers.plan(self, recipient, …)` recibe las dos identidades, exige ambas válidas, **se niega a publicar si son la misma** y deriva la clave del destinatario, que es lo que hace escritorio. Cuatro casos nuevos cubren justo eso: clave del destinatario, emisor siempre incluido como custodio, sin ámbito de dispositivo y rechazo de lista vacía o de publicación a sí mismo.
- **La lectura solo probaba el ámbito de dispositivo.** `VaultPointerKey.readKeys` devolvía dos claves (hoy y ayer) del dispositivo, pero la publicación compartida va sin dispositivo, así que un puntero publicado por un móvil enrolado era invisible para su propio dueño. Ahora devuelve hasta cuatro: compartida de hoy, compartida de ayer, de dispositivo de hoy y de ayer, sin duplicados.
- **La custodia local faltaba.** `OutboxVault.escrow` solo enviaba a custodios remotos, de modo que un móvil sin contactos con vault disponible nunca marcaba `vaulted` y el mensaje se perdía igual que antes. Ahora se guarda primero en el buzón propio (`VaultMailboxStore`, cifrado con Keystore) y basta con que **algo** acepte —local o remoto— para borrar la entrada del outbox; si ni siquiera el buzón local acepta (cuota agotada, sobre inválido) se conserva el comportamiento anterior. Tiene sentido porque el dispositivo que envía es, además, el primero que va a estar encendido cuando el destinatario vuelva.

- Pendiente en esta parte: republicar el puntero cuando cambian los custodios de un buzón ya existente. **El reparto por dispositivo no es una divergencia para chat**: comprobado que escritorio solo pasa `recipientDeviceId` en `groupControlSupport.ts` (eventos de sincronización dirigidos a un dispositivo propio concreto) y en `sync/transport.ts`; el chat normal llama a `replicateToVaults` sin ese argumento y guarda con `recipientDeviceId = null`, exactamente como hace el móvil. Portarlo es parte del paso 3, no del 2.

**`VAULT_RENEW` hecho (26/10/2026).** Sin renovación, una entrada del buzón desaparecía a los 60 días aunque el destinatario siguiera existiendo: pérdida silenciosa, y de las que no dan error. Portado con las dos mitades:

- **Custodio.** `vault/VaultRenewal.kt` valida el paquete con los mismos límites que `validationVaultGroups.ts` (hash de 64 hex, `newExpiresAt` numérico no negativo) y aplica las dos reglas de escritorio: **nunca acorta** (`max(pedido, actual)`) y **nunca pasa de 60 días** desde ahora. La gran diferencia es la autorización: escritorio acepta al emisor original **o a un custodio con `vouchScore ≥ 65`** (para que el `RepairWorker` pueda renovar por la red), y el móvil, que no tiene reputación, exige **ser el emisor original**. Es la opción conservadora: sin ese filtro, cualquier par conectado podría estirar cuotas ajenas indefinidamente, que es el DoS que escritorio ya parcheó con `BUG L`. Consecuencia asumida: el móvil no puede ejercer de `RepairWorker` para entradas de otros.
- **Emisor y custodio a la vez.** `OutboxVault.renewExpiring` busca las entradas propias que caducan en 15 días (`TTL − 45 días`, la misma ventana que `repairWorkerMaintenance.ts`), las renueva en el buzón local y avisa a los demás custodios con `VAULT_RENEW` firmado y sellado por `VaultChannel.renewPacket`. Los destinatarios del aviso salen del **puntero del destinatario** (`VaultDirectory.resolve`), y si no hay puntero se cae a todos los contactos, igual que hace la consulta. Corre al arrancar el nodo, con hilo propio y comprobando `isActive` entre entradas.
- 13 casos nuevos en `VaultRenewalTest` (renovación del dueño, no acortar, tope de 60 días, custodio ajeno rechazado, entrada desconocida, caducidad ausente o negativa, hashes malformados, comparación insensible a mayúsculas, ida y vuelta de `build`/`parse`, y los dos helpers del buzón). **Kotlin: 493 casos, 0 fallos.** Probado el arranque en dispositivo con este código: sin `UnsatisfiedLinkError` y con el nodo cargando su estado.

**Cuotas del vault: escritorio las condiciona por reputación; el móvil no puede.** En escritorio `network/vault/protocol/handlers.ts` calcula `computeScore(senderSid, contactosDirectos)` y **rechaza el `VAULT_STORE` si `vouchScore < 30`**, además de escalar la cuota en tres tramos: **50 MB** por defecto, **500 MB** con score ≥ 65 y **2,5 GB** con score ≥ 80. El móvil no tiene reputación —no hay ni una referencia a `vouch`/`reputation` en Kotlin ni en el bridge Go— así que implementa **el tramo más bajo (50 MB) para todos** y *no puede negarse a custodiar de un nodo sin confianza*. Es la asimetría de seguridad más grande que queda en el vault portado: un móvil custodiará para un par que escritorio rechazaría, y con el espacio del tramo inferior. Las dos salidas posibles son portar las atestaciones (`REPUTATION_*`, gossip de G-Set) o aceptar explícitamente que el móvil es un custodio de confianza baja y decirlo así en la UI; decidirlo antes de abrir la custodia en producción, y mientras tanto no prometer cuotas en Ajustes.

**Lo que queda del paso 2**: republicación de puntero al cambiar custodios, la prueba cruzada con escritorio como custodio real y decidir qué se hace con las cuotas por reputación. El detalle de por qué el móvil no tenía vault sigue aquí debajo.
**Hueco conocido (26/10/2026): el vault del móvil existe en piezas pero no está cableado.** Lo que corre de verdad para la entrega diferida sigue siendo solo `YggdrasilOutbox`: 8 envíos por mensaje y 30 intentos con backoff de 2 s × intento y tope de 60 s, unos 15 min. Pasada esa ventana el mensaje queda `failed` de forma permanente aunque el contacto vuelva, porque nadie guardó copia; en el mismo escenario el escritorio lo habría dejado en el buzón de un custodio con su puntero en la DHT. Consecuencia de producto: no prometer entrega offline en el móvil ni pintar estados `vaulted` hasta cablear el vault, que es el **paso 2 del orden vigente** y no el 8. La tarjeta «Almac. cedido» de Perfil ya no finge estar cargando: informa de que no está disponible, y sigue pendiente de rellenarse con el uso real del buzón. Al portarlo hace falta un sustituto del `vouch score`: el móvil no tiene reputación y `getDynamicReplicationFactor` de escritorio la usa para elegir entre 3 y 12 custodios, con `8` como valor de emergencia cuando falla. Medido sobre esa función el 25/10/2026: **0–69 → 12, 70–89 → 6, 90+ → 3**, y la parte que dependía del tiempo conocido nunca se ejecuta (`daysKnown` siempre 0 por falta de `createdAt`), así que hoy casi todo se replica 12 veces. Decidido el sustituto: **8 fijo** (ver arriba), porque el móvil no tiene reputación con la que alimentar la interpolación de escritorio; el detalle del consumidor está en [`../upper-desktop/Roadmap.md`](../upper-desktop/Roadmap.md) → «Reputación».

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

### B11 — Transporte local sin internet (Bluetooth y LAN)

> **⛔ Bloqueada a propósito: no se implementa sin hablarlo antes con el usuario.** Ni código, ni permisos `BLUETOOTH_*` en el manifiesto, ni dependencias, ni cambios en el framing o en la firma. La LAN local tampoco arranca de rebote. Antes hay que acordar cinco cosas: el alcance (¿proximidad entre personas o solo mis propios dispositivos?), el orden (LAN o Bluetooth primero), si se acepta el límite de privacidad de la radio (identificador estable visible al escanear), si se reabre `VerifySignedPacket` para admitir un origen sin Yggdrasil, y el presupuesto de batería del escaneo. El detalle de la decisión está en [`../ROADMAP.md`](../ROADMAP.md) → «B11». Cualquier petición de implementarlo se responde pidiendo esa conversación primero.

**Estado (24/10/2026): no iniciada y deliberadamente parada.** Paridad con Briar en lo que hace posible usarlo en un apagón: hablar con el dispositivo que está al lado sin ninguna conectividad IP.

**Depende de:** B9 (contrato de paquetes cerrado) y de la abstracción de enlace. **Bloquea a:** el modo «solo local» y cualquier prueba de mensajería de proximidad.

Base de Briar que se copia, con nombres verificados en `bramble-android`:

- **Bluetooth Classic (RFCOMM), no BLE.** Servidor con `listenUsingInsecureRfcommWithServiceRecord("RFCOMM", uuid)` y cliente con `createInsecureRfcommSocketToServiceRecord(uuid)`. Es un flujo de bytes orientado a conexión, así que el framing de 4 bytes que ya usa el móvil sirve tal cual.
- **Descubrimiento acotado.** `startDiscovery()` con tope (`MAX_DISCOVERY_MS = 10_000`), `cancelDiscovery()` antes de conectar y receptor de `ACTION_STATE_CHANGED`, `ACTION_SCAN_MODE_CHANGED` y fin de descubrimiento para no dejar el adaptador colgado.
- **Límite de conexiones.** Briar lo encapsula en `BluetoothConnectionLimiter` con factoría por plataforma (`AndroidBluetoothPlugin`, `JavaBluetoothPlugin`). En uPeer el límite tiene que ser del enlace, no solo del listener TCP de `YggdrasilInbound`.
- **El transporte es un plugin.** En Bramble la identidad se intercambia dentro del handshake cifrado sobre el mismo flujo y el resto del stack no sabe por dónde llegó el byte stream. uPeer necesita esa misma separación.
- **Tres modos locales.** Bluetooth, Wi-Fi en la misma red y Wi-Fi Direct cuando no hay router; más el intercambio por tarjeta. Los dos primeros cubren «sin internet, en el mismo sitio».

Trabajo:

- [ ] `TransportLink` en Android (`write`/`read`/cierre/estado/origen) con dos implementaciones: `YggdrasilLink` sobre el bridge actual y `BluetoothLink` sobre RFCOMM. `YggdrasilProtocol.handleFrame` deja de depender del `Bridge` concreto para transportar.
- [ ] Origen transport-agnóstico en el paquete firmado (`senderEndpoint` sellado o dominio `LOCALv1`) para poder firmar sin Yggdrasil, sin relajar `VerifySignedPacket` cuando la dirección sí existe. Vector JS↔Go↔Kotlin.
- [ ] Admisión y rate limit por clave de dispositivo y certificado, no por `ip:<addr>`; `InboundRateLimiter` necesita claves por enlace.
- [ ] Descubrimiento Bluetooth con permisos de Android 12+ (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`), nombre de dispositivo sin identidad y `upeerId` solo tras el handshake cifrado.
- [ ] LAN sin internet: sockets locales y anuncio en la subred reutilizando `KademliaSeedLoader`, sin abrir un segundo mecanismo de confianza.
- [ ] Relleno de longitud constante por enlace, para que el tamaño no revele si es `CHAT`, `READ` o `SYNC_ENVELOPE`.
- [ ] Modo «solo local» en Ajustes · Conexión y estado del enlace en la cabecera del chat, con comparación de huella para verificar identidad en presencia.
- [ ] Presupuesto de batería: escaneo por ventanas, cancelación al cerrar y medición de 30 min antes de activarlo por defecto.
- [ ] Pruebas: dos móviles sin red, móvil↔escritorio en LAN, RFCOMM cortado a mitad de frame, mismo mensaje por dos radios sin duplicar y MAC distinta tras reiniciar el Bluetooth.

**Salida:** dos dispositivos sin conectividad IP intercambian texto y recibo de lectura por Bluetooth, con identidad verificada por huella, relleno constante y batería medida; el mismo mensaje recibido por dos radios no se duplica y el transporte no altera el estado criptográfico del contacto.

**No hacer:** inventar una dirección Yggdrasil falsa para firmar; relajar la verificación para todos los paquetes; tratar el emparejamiento Bluetooth como prueba de identidad; dejar el escaneo siempre encendido; ni presentar Bluetooth como sustituto del vault.

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

### P2 — Protocolo headless persistente (chat con contacto existente completado; el residuo de B6 es el bloqueo activo)

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
8. [ ] DHT Kademlia, bootstrap y roaming. B0–B5 están cerradas localmente. **Corregido el 25/10/2026:** esta lista ya no es el orden de trabajo. B8 está cableado en el servicio, B7 solo espera la prueba de roaming y B10 no empieza tras B9 sino tras el runtime en modo dispositivo; el vault va antes que la sincronización. Ver [Orden de ejecución vigente](#orden-de-ejecución-vigente-25102026).
9. [ ] Paridad social, vault, grupos, archivos y llamadas (fases 4–10, solo tras B9; vault/multidispositivo tras B10).
   - [x] Eventos de mensaje del protocolo móvil: `READ`, `TYPING`, `CHAT_DELETE`, `CHAT_REACTION` y la tarjeta de contacto (como contenido de `CHAT`). Ver «Inventario de paridad con escritorio».
   - [ ] Sincronización multi-dispositivo con los nueve `SYNC_EVENT_KINDS`, relay y backlog propios del móvil.
   - [ ] Archivos (`FILE_*`), grupos (`GROUP_*`) y llamadas (`CALL_*`) con interoperabilidad real contra escritorio.

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

- Tras validar la firma, descifrar y persistir un `CHAT`, se responde con `{ type: "ACK", id, status: "delivered" }` firmado con la identidad propia. El recibo de lectura es un `READ` aparte, firmado y sellado, que se envía al abrir la conversación.
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

## Fases de producto (solo después de la puerta de B9; Producto 11 solo al final)

B0–B9 son la secuencia obligatoria para red, identidad de cuenta e interoperabilidad; B10 es la sincronización multidispositivo. Las fases siguientes describen producto y no deben adelantarse para compensar una salida incompleta de B9. Las fases de identidad, transporte e integración Android ya tienen su estado en B0–B10; el Producto 6 no se porta sobre el atajo actual de Desktop. El Producto 11 (blogs) es la última de la cola y no se empieza con la mensajería sin cerrar.

**El número de cada producto es un identificador, no su turno.** El orden de ejecución es el del apartado «Orden de ejecución vigente»: primero el runtime en modo dispositivo, después el Producto 6 (vault, que **no** espera a B10), después `SYNC_*` para cerrar B10, y a partir de ahí archivos antes que grupos. Ese último cambio es deliberado: `FILE_*` es más pequeño, desbloquea las notas de voz en el chat 1:1 y evita portar grupos a medias, porque los mensajes de grupo llevan adjuntos desde el primer día en escritorio. Los números 7 y 8 de esta sección se leen, por tanto, en orden inverso.

### 4. Contactos y chat directo mínimo E2EE

Portar handshake (incluido `powProof` Argon2id para contactos nuevos), X3DH, signed pre-keys, conversión Ed25519/Curve25519, sealed boxes, Double Ratchet, persistencia transaccional, ACK, DR_RESET y outbox. Habilitar contactos y una conversación de texto vertical de extremo a extremo antes de sumar funciones de chat.

**Estado actual:** parcial. Las primitivas están portadas, el ratchet se persiste, el servicio descifra `CHAT` con X3DH, envía ACK, entrega mensajes salientes con outbox, notifica mensajes entrantes y la UI muestra la conversación. `DR_RESET`, SPK obsoleta, misma SPK idempotente por `resetId`, ACK perdido, duplicados, peer inalcanzable, reconexión de red, rate limiting y replay idempotente están probados. Queda pendiente el alta de un contacto nuevo desde móvil: `HANDSHAKE_REQ` no incluye el `powProof` Argon2id que Desktop exige para contactos nuevos, ni `addresses`/`ephemeralPublicKey`; el chat cruzado actual usa un contacto ya conocido. También faltan cambio de identidad del contacto y offline prolongado.

**Salida:** primer contacto y mensajes posteriores pasan en ambas direcciones con Desktop; se prueba reinicio, duplicado, ACK perdido, contacto offline y cambio de clave; ratchet/outbox no pierden estado.

### 5. Paridad de chat y UX de producto

Portar historial/paginación, respuestas, edición, borrado, reacciones, recibos, presencia, búsqueda y notificaciones. Completar navegación y estados visuales contra funcionalidades ya verificadas, no contra pantallas de Desktop sin backend.

**Salida:** cada acción tiene persistencia, protocolo y pruebas cruzadas; estados offline/pendiente/entregado no se confunden.

### 6. Vault y continuidad multidispositivo (Producto 6)

**Corregido el 25/10/2026: no depende de B10.** El vault y la sincronización son protocolos separados; solo comparten el almacén de mensajes y, por tanto, la regla de deduplicación por `messageId`. Lo único que bloquea esta fase es el runtime en modo dispositivo (paso 1 del orden vigente), porque el buzón se identifica por `deviceId` y no tiene sentido montarlo sobre una identidad que aún lleva la raíz. B6 aporta cuenta, registro autenticado de dispositivos, claves independientes y revocación. Esta fase porta la capa de producto: vault, tokens, punteros DHT, recuperación de eventos/adjuntos, deduplicación, cuotas y UI de estados. No se reimplementa el self-sync actual de Desktop con identidad/SPK compartidas. Tampoco hay `vouch score` en el móvil: el factor de replicación necesita un sustituto determinista.

**Salida:** escenarios prolongados offline y recuperación Desktop↔móvil sin pérdidas ni duplicados visibles, con límites de disco/red; un dispositivo revocado deja de recibir y descifrar mensajes cifrados con el registro posterior al epoch. Un mensaje a un contacto offline ya no acaba en `failed`.

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

### 11. Blogs P2P (extra; solo con la mensajería cerrada)

Función de producto al final de la cola, no un paso intermedio: un muro corto por identidad, en la línea de los blogs de Briar, donde publicas texto firmado que tus contactos leen y pueden recompartir con los suyos. Sin servidor, sin descubrimiento global y sin algoritmo: solo se ve lo que publican tus contactos y lo que ellos recomparten, en orden cronológico. Es el equivalente a Twitter con privacidad por defecto, pero el «tuit» vive en los dispositivos de quien lo lee.

Lo que se copia de Briar (verificado en `briar-api/src/main/java/org/briarproject/briar/api/blog/BlogManager.java`):

- El blog es un **cliente propio del protocolo** (`org.briarproject.briar.blog`, con versión mayor/menor), no un tipo de mensaje dentro del chat.
- Un post es **cabecera firmada** (autor, marca de tiempo, padre y firma) más cuerpo de texto, y se guarda como contenido propio, no como mensaje de conversación.
- **Recompartir no reenvía el post: lo vuelve a firmar quien lo comparte.** `addLocalComment(author, groupId, comment, parentHeader)` crea un post nuevo del que comparte apuntando al header del padre, así que la firma del autor original viaja dentro y el lector puede seguir atribuyéndolo a quien lo escribió. Un comentario es el mismo mecanismo con texto.
- La suscripción es explícita (`getBlogs`, `getBlogIds`) y solo para autores conocidos; el estado de leído es por post (`setReadFlag`) y un blog se puede borrar (`canBeRemoved`).

Por qué no se puede hacer hoy con lo que ya hay:

- **La autenticidad actual es por salto, no por contenido.** `SealedControl.frame` firma lo que envía el emisor inmediato y el receptor lo valida contra ese par. Si B reenvía un post de A, la firma que viaja es la de B. Hace falta una **firma de contenido** con la clave de identidad del autor, independiente del sobre; el sobre queda solo para confidencialidad y para atribuir el salto.
- **El `id` es un UUID y se usa como clave de deduplicación.** Un post recompartido por dos caminos debe reconocerse por contenido, así que necesita un identificador derivado (hash de los bytes firmados) con su propio dominio de validación, sin relajar `ReplayPolicy.validMessageID` ni el resto de ids del protocolo.
- **Nada se entrega a quien no es contacto.** Hoy no hay forma de hacer llegar un post de un autor desconocido sin abrir conversación ni disparar la solicitud de mensaje automática; y las solicitudes solo las ve quien las recibe.
- **Desktop no tiene blogs.** `verifiedPacketRouter` cae en `default` y solo registra «Unknown packet», así que un `BLOG_POST` de Android es inofensivo allí, pero tampoco hay paridad: la función nace Android-first hasta que Desktop la implemente.
- **Sin multimedia al principio.** Las imágenes y los adjuntos dependen del Producto 8; la primera versión es solo texto con tope de longitud, o el almacenamiento se descontrola antes de tener cuotas.

Trabajo:

- Canonicalización y firma de contenido: `BLOG_POST` firmado por el autor, `contentId = hash(bytes firmados)` y cabecera con `parentId` para recompartes y comentarios.
- Tabla propia `blog_posts` (contenido direccionado, autor, padre, marca de tiempo, firma, leído) con deduplicación por `contentId` y topes por blog y por bytes. `sync_events` de B10 no sirve para esto.
- Un `SYNC_EVENT_KIND` nuevo, para que tu propio blog exista también en tus otros dispositivos.
- Entrega a suscriptores con el outbox y el ACK existentes; `BLOG_REQ`/`BLOG_RES` para recuperar huecos de forma acotada.
- Entradas en `InboundRateLimiter` y admisión por contacto; un post de un tercero no crea conversación, no notifica y no aparece fuera del hilo de quien lo recompartió.
- UI: pestaña de blogs con la misma línea de tiempo de Chats, detalle de post con recompartir y comentar, y suscripción por contacto en su ficha, usando los tokens de `src/constants/theme.ts` y sin lenguaje visual nuevo.

**Salida:** dos dispositivos intercambian publicaciones y recompartes con atribución verificable del autor original; el mismo post que llega por dos caminos se muestra una sola vez; un post de un autor desconocido no abre conversación ni notifica; los topes de tamaño y la recuperación de huecos están probados.

## Inventario de paridad con escritorio (23/10/2026)

Cierre de brechas medido contra el código de `upper-desktop`, no contra lo que la UI aparenta. Todo lo que sigue está sin probar entre clientes salvo que se indique lo contrario: las 215 pruebas nativas cubren códec, políticas y DHT, pero **el único flujo verificado de extremo a extremo es el chat de texto de un contacto conocido con su ACK**.

### Tipos de paquete del protocolo

| Paquete | Escritorio | Móvil | Prueba cruzada |
|---|---|---|---|
| `HANDSHAKE_REQ` / `HANDSHAKE_ACCEPT` | Sí, con binding de dispositivo | Sí | Vectores de canonicalización y firma; flujo real en dispositivo |
| `CHAT` | Sí | Sí (`sendChat`) | Verificado en dispositivo contra Desktop |
| `ACK` | Sí | Sí (`ControlReplies`) | Verificado con el chat |
| `DR_RESET` | Sí | Sí (`DrResetPolicy`, `DrResetReplayCache`) | Pruebas JVM; sin prueba real de reinicio de ratchet |
| `CHAT_UPDATE` (edición) | Sí, con `version` y política de conflicto | Sí (`editChat`, `ChatEditPolicy`) | Pruebas JVM; sin prueba cruzada |
| `READ` (recibo de lectura) | Sí | Sí (`ReadReceipts`, `ControlReplies`) | Pruebas JVM del frame; falta prueba cruzada real |
| `TYPING` (escribiendo) | Sí | Sí (`TypingIndicators`, `ControlReplies`) | Pruebas JVM; falta prueba cruzada real |
| `CHAT_DELETE` | Sí | **Ausente** | No probado |
| `CHAT_REACTION` | Sí | **Ausente** | No probado |
| `CHAT_CONTACT` (tarjeta de contacto) | Sí | **Hecho como contenido de `CHAT`**: se envía y se pinta la tarjeta, y el resumen de la lista lo dice. El paquete `CHAT_CONTACT` de escritorio no se emite en ningún sitio, así que no se implementa | Falta la prueba con escritorio real |
| `GROUP_MSG` / `GROUP_ACK` / `GROUP_INVITE` / `GROUP_UPDATE` | Sí, con sender keys, epoch y control de admin | **Ausente** | No probado |
| `FILE_PROPOSAL` / `FILE_START` / `FILE_ACCEPT` / `FILE_CHUNK` / `FILE_CHUNK_ACK` / `FILE_ACK` / `FILE_END` / `FILE_DONE_ACK` / `FILE_CANCEL` | Sí, chunked con AES-256-GCM y progreso | **Ausente** | No probado |
| `VAULT_STORE` / `VAULT_QUERY` / `VAULT_DELIVERY` / `VAULT_ACK` / `VAULT_SYNC_REQ` / `VAULT_SYNC_RES` / `VAULT_RENEW` | Sí, buzón por dispositivo | **Ausente** | No probado |
| `CALL_OFFER` / `CALL_RING` / `CALL_ACCEPT` / `CALL_REJECT` / `CALL_BUSY` / `CALL_CANCEL` / `CALL_END` / `CALL_MEDIA` / `CALL_MEDIA_UPDATE` / `CALL_META` | Sí, con relay y grupos | **Ausente** | No probado |
| `PING` / `PONG` (keepalive de nivel de protocolo) | Sí | **Ausente**; la conexión la mantiene el servicio, no el protocolo | No probado |
| `DHT_*` (12 tipos) | Sí | Sí, códec y Kademlia con vectores | Vectores Go/JVM y fixtures de Desktop |
| `SYNC_HELLO` / `SYNC_ENVELOPE` / `SYNC_HISTORY_REQ` / `SYNC_HISTORY_RES` / `SYNC_ACK` | Sí, con relay, firma de origen y backlog | **Ausente** | No probado |

### Sincronización multi-dispositivo: los nueve eventos

`SYNC_EVENT_KINDS` de escritorio son `MESSAGE_UPSERT`, `MESSAGE_STATUS`, `MESSAGE_EDIT`, `MESSAGE_DELETE`, `CHAT_CLEAR`, `REACTION_SET`, `GROUP_UPSERT`, `GROUP_DELETE` y `CONTACT_PROFILE`. El móvil no implementa ninguno: no hay store de eventos, ni cursor, ni ACK por dispositivo. Los estados `pending`/`synced`/`vaulted`/`delivered`/`failed` que el roadmap de B10 da por observables solo existen en escritorio.

### Funciones de escritorio sin equivalente en móvil

| Función | Dónde vive en escritorio | Estado móvil |
|---|---|---|
| Llamadas 1:1 de audio y vídeo | `src/features/call/` (señalización, `mediaPipeline`, `webCodecsSession`, codecs, calidad) | Nada: la pestaña Llamadas es un estado vacío |
| Llamadas de grupo y relay de medios | `callSignaling.ts`, `callManager.ts`, `binaryCallMedia.ts` | Nada |
| Compartir pantalla | `ScreenSharePicker.tsx` | Nada |
| Historial de llamadas | `CallsPanel.tsx` | Nada |
| Mensajes de grupo | `messaging/groups.ts`, `groupControl.ts`, `groupState.ts` | Nada |
| Llamada de grupo como evento de chat | `messaging/groupCallEvent.ts` | Nada |
| Archivos, imágenes, PDF y audio | `network/file-transfer/`, `features/chat/file/` | Nada; el clip del composer avisa «pendiente» |
| Visor de medios y miniaturas | `features/chat/media-viewer/` | Nada |
| Notas de voz | `features/chat/input/RecordingBar.tsx` | Nada; el micrófono avisa «pendiente» |
| Responder, reenviar y reaccionar | `MessageReply.tsx`, `ForwardModal.tsx`, `MessageReactions.tsx`, `MessageContextMenu.tsx` | Editar, reaccionar y responder sí; reenviar no |
| Borrar mensaje y vaciar chat | `chatMutations.ts` | Nada |
| Escribiendo y presencia | `messaging/heartbeat.ts` | Nada |
| Búsqueda dentro de la conversación | `ChatSearchBar.tsx` | Avisa «pendiente» |
| Vista previa de enlaces | `og-fetcher.ts`, `LinkPreviewCard.tsx` | Nada |
| Tarjeta de contacto | `ContactCard.tsx` | Solo se muestra el QR propio en Perfil |
| Selector de emoji | `EmojiPicker.tsx`, `emojiData.ts` | Nada |
| Buzón cifrado y vault | `network/vault/` | Nada |
| Revocar dispositivo propio | IPC `revoke-authorized-device` | Equivalente en Perfil → dispositivos: `revokeAccountDevice` y el servicio descarta sus paquetes |
| Estados de sincronización | `getSyncStatus` + evento `sync-status` | Nada |

### Cobertura de pruebas real

| Qué | Estado |
|---|---|
| Chat de texto 1:1, envío, ACK, edición | Verificado en dispositivo contra Desktop |
| Admisión de dispositivo, certificado y registro | Vector compartido + prueba en dispositivo |
| Enrolamiento OOB en ambas direcciones | Vector compartido + `androidTest` |
| DHT: códec, Kademlia, store/lookup, `DHT_UPDATE` | Pruebas JVM y vectores; **sin roaming real móvil↔escritorio** |
| Reacciones, borrado para todos, tarjetas, respuestas, reenvíos | **Solo dentro de cada plataforma**; sin prueba cruzada |
| Reintento de fallidos, recibos de lectura, presencia y escritura | **Solo dentro de cada plataforma**; sin prueba cruzada |
| Número de seguridad comparable entre plataformas | **Imposible hoy**: los algoritmos son distintos |
| Llamadas (1:1, grupo, relay, rendimiento, escalabilidad) | Solo en escritorio; el móvil no participa |
| Grupos (fanout, escala) | Solo en escritorio |
| Transferencia de archivos | Solo en escritorio; el móvil no tiene `FILE_*` |
| Vault y recuperación | Solo en escritorio; el móvil no tiene vault |
| Sincronización multi-dispositivo y relay | Solo en escritorio (`sync-status`, `multi-process`); el móvil no tiene `SYNC_*` |
| Presión de memoria, batería y Doze | Sin medir |

### Interoperabilidad real: la deuda que la paridad de UI esconde

El port se ha centrado en que el móvil *tenga* las funciones de escritorio, no en que ambas se entiendan al usarlas juntas. Eso deja una deuda concreta que conviene no confundir con «cerrado»:

- **El número de seguridad ya coincide entre plataformas.** `SafetyNumber` (móvil) y `computeSafetyNumber` (escritorio) ordenan las dos claves públicas, las concatenan y hacen SHA-256 de 20 bytes en 8 grupos de 5, con los **mismos vectores** en `SafetyNumberTest` y `safetyNumber.test.ts`. `get-contact-security` expone `hasSession`, `hasContactKey` y el número a la vista de Cifrado de escritorio, que antes no mostraba ninguno: la vista distingue «no hay sesión», «hay sesión pero no clave del contacto» y «no se pudo calcular». Ojo: `computeKeyFingerprint` (BLAKE2b-128 de una clave) sigue vivo para los avisos TOFU de cambio de clave y no es un número de seguridad.
- **La tarjeta de contacto ya se lee en las dos direcciones.** El móvil enseña su QR en Perfil y en la hoja de tarjeta, y el escritorio incorpora `QrScannerModal` (`getUserMedia` + `jsQR`) en `AddContactModal`, con `parseContactQrPayload` exigiendo `uPeerID@dirección` y rechazando con mensaje el QR de solo identidad. Salvedad honesta: **no se ha probado con una cámara física**; lo verificado es la decodificación de un QR generado con la misma librería.
- **Lo portado en las últimas semanas está probado contra sí mismo.** Reacciones, borrado para todos, tarjetas, respuestas, reenvíos, reintento de fallidos, recibos de lectura, presencia y escritura tienen pruebas en su plataforma y ninguna cruzada. Un `CHAT_REACTION` que el móvil emite puede estar bien formado y aun así no ser lo que escritorio espera.
- **La paridad es asimétrica por diseño.** El móvil no tiene `FILE_*`, ni grupos, ni llamadas, ni vault, ni `SYNC_*`. «Probar adjuntos Desktop↔móvil» no es una prueba pendiente: es una función que solo existe en un lado.
- **Revisión estática de conformidad del plano de control: sin discrepancias.** Se compararon campo a campo emisores y receptores de `CHAT_REACTION`, `CHAT_DELETE`, `READ`, `ACK`, `TYPING` y tarjeta de contacto. Los identificadores son UUID en minúsculas en ambos lados (`ReplayPolicy.validMessageID` frente a los mensajes del escritorio); la reacción viaja con `msgId`/`emoji`/`remove` en los dos; el escritorio atribuye la reacción al `upeerId` del transporte y no al `senderUpeerId` del payload, así que acepta el frame del móvil, que no lo incluye; el borrado usa `msgId` en los dos; `READ` usa `id` y el escritorio lo lee con esa misma clave; y la tarjeta se serializa con el mismo JSON (`type: contact_card`, `text`, `contact.{name,address,upeerId,publicKey,avatar}`). El contenido `CHAT` lo cifra en ambos casos el **mismo bridge Go** (`buildChat`/`buildLinkedChat`), así que el sobre no puede divergir por reimplementación. Lo que sigue sin probarse es el comportamiento: estados, orden y efecto en la base de datos de cada cliente.

Nada de esto invalida el trabajo hecho. Significa que la frase «mensajería cerrada» solo es cierta dentro de cada plataforma, y que la siguiente tanda debe ser de interoperabilidad, no de funciones nuevas.

### Lo que tampoco está cerrado en escritorio

No todo lo que el móvil debe alcanzar está terminado en el otro lado. Antes de portar conviene confirmar el contrato:

- `CHAT_REACTION`, `CHAT_DELETE` y `CHAT_UPDATE` siguen anotados como «Preview for phase 11» en `upper-desktop/src/main_process/network/types.ts` aunque ya tienen UI y pruebas de entrega. La anotación está obsoleta o el contrato no se ha declarado congelado.
- La ventana legacy `ALLOW_WITH_AUDIT` sigue abierta en escritorio para contactos sin claves por dispositivo; cerrarla es requisito de B6 y afecta a cómo el móvil firma y recibe.
- El modo dispositivo de escritorio ya opera sin raíz, pero la mensajería por dispositivo solo cubre CHAT directo y grupos con binding: cualquier port móvil debe asumir ambos caminos hasta que la migración termine.

### Orden de trabajo para cerrar la paridad

Primero interoperabilidad, después funciones nuevas: de nada sirve portar más si lo portado no se entiende con el otro lado.

1. ~~**Unificar el número de seguridad**~~ — **hecho**: `computeSafetyNumber` en escritorio con los vectores del móvil, IPC `get-contact-security` y la vista de Cifrado mostrándolo.
2. **Verificación cruzada real de lo ya portado** — reacciones, borrado para todos, tarjetas, respuestas, reenvíos, reintento de fallidos, recibos de lectura, presencia y escritura, con móvil y escritorio conectados. La revisión estática de conformidad ya está hecha y no encontró discrepancias (ver «Interoperabilidad real»); lo que falta es la prueba con los dos clientes vivos. Barato en código y valida varias semanas de trabajo.
3. ~~**Escáner de QR en escritorio**~~ — **hecho**: `QrScannerModal` (`getUserMedia` + `jsQR`) en `AddContactModal`, con `parseContactQrPayload` exigiendo `uPeerID@dirección` y rechazando el QR de solo identidad. Salvedad: no se ha probado con una cámara real; se verificó la decodificación de un QR generado con la misma librería.
4. **Sincronización multi-dispositivo en Android** — store de eventos, `SYNC_HELLO`/`SYNC_ENVELOPE`/`SYNC_HISTORY_*`, relay y ACK, con los nueve tipos y `CONTACT_PROFILE`.
5. **Archivos** — `FILE_*` con picker, chunked, hash y progreso; es el prerrequisito de las notas de voz y de la paridad simétrica.
6. **Grupos** — `GROUP_*` con sender keys y control de admin.
7. **Llamadas** — señalización primero (ring/accept/reject/end), luego media UDP, y solo después vídeo y grupos.
8. **Vault y entrega diferida** — buzón por dispositivo y recuperación.
9. **Cierre de UI** — notificaciones con enlace profundo al chat, ajustes reales y revocación de dispositivos.

## Pendiente en la UI móvil

Lista viva de lo que la interfaz muestra pero todavía no ejecuta, o de lo que falta por crear. Cualquier elemento nuevo entra aquí antes de tocar código.

| Pantalla | Estado | Qué falta |
|---|---|---|
| Chats · búsqueda | Hecha | `Input` real que filtra por título, identidad y vista previa con `filterChatEntries`. **Sin borde**, como `SidebarSearch`: caja de 35 px, radio 8, lupa dentro y aspa para limpiar. El mismo patrón se aplica a Contactos y al selector de contactos |
| Chats · filtros | Hechos | Mismo juego que escritorio (`SidebarFilter`): `Todos`, `No leídos`, `Favoritos` y `Grupos`, combinados con la búsqueda. **`Archivados` deja de ser filtro**: archivar es una acción por conversación y su conversación sigue visible, igual que en Desktop. `Grupos` muestra su estado vacío hasta que exista origen de datos, sin inventar conversaciones. Los chips copian el `Chip` de Joy: `solid`/`primary` activo, `soft`/`neutral` inactivo, radio de píldora |
| Chats · menú contextual | Hecha | Fijar, silenciar y archivar se guardan por conversación en `upeer.conversationFlags`: los fijados suben al principio con su chincheta, los silenciados muestran la campana tachada y el servicio omite sus notificaciones, y los archivados salen de «Todos» con un filtro «Archivados» propio. «Marcar como leído» actúa; «Favoritos» y «Vaciar chat» siguen avisando de «próximamente» |
| Chats · nueva conversación | Parcial | «Nueva conversación» y «Nuevo grupo» avisan de «próximamente»; la lista de contactos sí abre el chat |
| Contactos · alta | Hecha | Falta la tarjeta propia en QR. La reputación deja de ser dato de producto: solo se avisa cuando es muy mala |
| Chats · solicitudes | Hecha | El primer mensaje de un remitente sin contacto entra en la lista como una conversación más; al abrirla, el cuerpo muestra la pantalla de solicitud con Bloquear y Aceptar y conectar. Si ya existía conversación, se acepta sola |
| Chats · bloqueo | Hecha | Bloquear desde una solicitud o desde la ficha oculta la conversación y el servicio descarta sus paquetes antes de descifrar, sin notificar |
| Contactos · tarjeta propia en QR | Pendiente | El móvil debe poder mostrar su tarjeta de contacto en QR para que el escritorio la escanee, además del QR de identidad que ya vive en Perfil |
| Contactos · sincronización | Pendiente | Altas y cambios de alias viajan por `CONTACT_PROFILE` de B10: falta emitirlos desde Android |
| Llamadas | Sin runtime | La pestaña no tiene motor de llamadas ni historial real |
| Perfil · edición | Parcial | Alias y avatar se guardan; faltan privacidad, notificaciones y backup |
| Perfil · dispositivos | Hecha | Lista el registro firmado y abre el autorizador de invitación |
| Mensajes · estados | Parcial | `pending`/`sent`/`delivered`/`read`/`failed` son reales: el ACK marca `delivered` y el `READ` marca `read` en ambos sentidos. `synced` y `vaulted` siguen pendientes de la sincronización multi-dispositivo |
| Ajustes · índice | Hecho | Ocho secciones con contenido real salvo «Almacenamiento»: Privacidad, Seguridad, Bloqueados, Notificaciones, Apariencia, Conexión, Acerca de |
| Notificaciones | Hecha | `UPeerNotifications` publica por conversación con previsualización privada, enlace `upeer://chat/<id>` y acción «Marcar leído», y respeta las preferencias: mensajes de contactos, solicitudes de desconocidos y sonido. La acción «Desconectar» de la notificación persistente **sale de ahí** (26/10/2026): desconectar el nodo pasa a Ajustes. Faltan agrupación entre conversaciones y presencia |
| Ajustes · privacidad | Hecha | «Confirmaciones de lectura» se aplica en el servicio y «Conectado»/«Última vez» deciden qué enseña la cabecera del chat y la ficha con la presencia real derivada de `lastSeen` |
| Ajustes · seguridad | Hecha | Estado del registro, lista de dispositivos con revocación confirmada y ayuda en popup; faltan PIN, revelar frase y borrado de cuenta, que necesitan bridge nativo |
| Ajustes · bloqueados | Hecha | Lista real desde `contact-store` con desbloqueo confirmado; el servicio recibe la lista al desbloquear |
| Ajustes · notificaciones | Hecha | Los tres interruptores se persisten y viajan al servicio (`setNotificationPrefs`); el canal de sonido se elige por preferencia y la notificación se omite si el tipo está apagado |
| Ajustes · conexión | Parcial | Muestra estado, dirección, mapa de red y detalles técnicos. Falta el interruptor **«Mantener conectado en segundo plano»** (activado por defecto) y el botón de desconectar, que salen de la notificación persistente: ver [Servicio en segundo plano y desconexión desde Ajustes](#servicio-en-segundo-plano-y-desconexión-desde-ajustes-26102026) |
| Ajustes · apariencia | Hecha | Selector Sistema/Claro/Oscuro aplicado al instante en Tamagui, pestañas, barra de estado y chrome del sistema, y recordado entre arranques. El **tamaño de texto** (pequeño/normal/grande) escala con `useTextScale` toda la app: mensajes, listas, Chats, cabecera, Ajustes, Contactos, fichas, Perfil, hojas, diálogos, snackbar, solicitud y arranque. Solo quedan fuera el glifo del emoji y el mapa de red, que dibuja sobre `Svg` |
| Ajustes · almacenamiento | Placeholder | El móvil no tiene vault: falta bridge de estadísticas y limpieza, e historial de archivos |
| Ajustes · acerca de | Hecha | Versión real desde `expo-constants`, licencia GPLv3, código abierto, sin publicidad y sin datos recopilados |
| Perfil · revocar dispositivo | Hecha | Cada dispositivo que no es el actual lleva un icono de papelera que pide confirmación, llama a `revokeAccountDevice` y refresca la lista; los revocados se listan debajo con su fecha. Si el dispositivo local está revocado, el aviso ofrece «Volver a vincular este dispositivo» con el flujo de emparejamiento. Falta que la revocación viaje por B10 al resto de dispositivos |
| Chat · cabecera | Parcial | «Info. del contacto» abre la ficha completa y «Buscar en la conversación» abre la barra de búsqueda; videollamada y llamada de voz avisan «pendiente». Cuando la conversación es una solicitud solo quedan volver, avatar y menú |
| Chat · búsqueda | Hecha | La barra sustituye al compositor con contador `1 de N`, flechas anterior/siguiente y resalte de la burbuja encontrada; cierra y devuelve el compositor |
| Ficha del contacto | Hecha | Pantalla completa `/contact/[upeerId]` con la distribución de escritorio: hero (avatar 96, alias, identidad y presencia), fila Llamar·Vídeo·Buscar, identidad y dirección copiables, acciones de conversación y zona de riesgo. Bloquear/desbloquear, eliminar, compartir, silenciar, archivar, favoritos y cerrar sesión son reales; «Cifrado» abre la hoja con el número de seguridad; vaciar chat se muestra atenuado con «Pronto» |
| Ficha de grupo | Pendiente | No hay grupos en el móvil; cuando existan `GROUP_*` debe replicar la misma pantalla con miembros y administración |
| Chat · composer | Parcial | Adjuntar archivo y nota de voz avisan «pendiente». **No hay ningún `FILE_*` en el móvil**: adjuntos, imágenes, vídeo, audio y documentos no existen, así que tampoco hay reintento de archivos que probar |
| Chat · mensajes | Hecha | Editar, reaccionar, borrar para todos, responder (cita con autor y extracto), reenviar a uno o varios contactos y **reintentar un envío fallido**; el estado de entrega es real (`pending`/`sent`/`delivered`/`read`/`failed`), con 8 reenvíos sin ACK antes de darse por fallido |
| Chat · presencia | Parcial | «Escribiendo…» real en la cabecera y en la vista previa de la lista, y presencia (`En línea` dentro de 65 s y `Última vez …` después) desde `lastActive` del servicio. Pendiente: **la etiqueta que ve escritorio sobre el móvil casi nunca se refresca** porque el móvil sólo anuncia actividad al montar y al volver a primer plano, y sólo a peers vistos en los últimos 5 min (ver «Presencia desde el móvil: por qué escritorio no se entera») |
| Accesibilidad | Parcial | El tamaño de texto de Apariencia ya escala toda la app; falta revisión con lector de pantalla |

### Borrado, bloqueo y solicitudes de mensaje (23/10/2026)

Modelo acordado: **la lista de contactos es la autoridad**. No hay paquete nuevo ni paso extra para el remitente; el cliente que escribe no hace nada distinto.

- Al borrar un contacto se purga también su conversación: `deleteContactAndConversation` borra la fila de `upeer.db` y llama a `deleteConversation` del puente nativo, que vacía los mensajes de ese `upeerId` en el estado cifrado. Se conservan `peers` y `sessions` para que un mensaje posterior siga descifrando.
- Si vuelve a escribir, la conversación reaparece **sin ficha de contacto** y `classifyConversations` la marca como **solicitud de mensaje**. La solicitud **no es una tarjeta en la lista ni un botón en la cabecera**: entra como una conversación más en Chats y, al abrirla, **el cuerpo del chat se sustituye** por la pantalla de solicitud, igual que `IncomingRequestChat` en escritorio. Solo la ve quien la recibe; el remitente ve una conversación normal.
- La pantalla de solicitud sigue la distribución de escritorio: avatar con anillo, título «Solicitud de conexión», párrafo explicativo con el alias en negrita, «Solicitud recibida · fecha» cuando hay mensaje, tarjeta de confianza y los botones **Bloquear** y **Aceptar y conectar**. La cabecera durante una solicitud solo deja volver, avatar y menú; el compositor se oculta.
- **La solicitud es automática y solo del primer contacto.** `decideRequestState({contactStatus, hasOutgoing, hasIncoming})` decide entre `none`, `request` y `auto-accept`: si la conversación ya existía (hay algún mensaje saliente nuestro), el contacto se crea solo como `connected` al abrir el chat, sin pedir nada. Así, borrar y volver a escribir nunca deja al remitente esperando.
- **Un contacto `pending` se promueve a `connected` con el primer mensaje** (26/10/2026). Añadir a mano guarda el contacto como `pending`, pero el móvil no envía ningún `HANDSHAKE_REQ` hasta que se escribe: mantener ese estado con la conversación activa dejaba «Esperando respuesta» para siempre. `decideRequestState` devuelve `auto-accept` para `pending` en cuanto hay un mensaje en cualquier dirección, y la promoción conserva `address`, `publicKey`, `createdAt` e `isFavorite` del contacto existente (`saveContact({ ...contact, status: 'connected' })`), porque reescribir la fila con campos vacíos perdía la dirección del par.
- **Contactos refresca solo cada 3 s** (26/10/2026). La lista solo cargaba al montar: al volver de un chat donde el contacto se había promovido, seguía mostrando el estado anterior. Ahora `ContactsScreen` repite `getContacts`/`getConversations` cada 3 s, el mismo patrón que Chats (2 s) y el contador de no leídos (3 s); un fallo del sondeo solo deja un `console.warn`, no el aviso de error de la carga inicial.
- **`pending` no se explica al usuario.** Mientras no hay mensajes, el estado se rotula «Pendiente», igual que el panel de contacto de escritorio; se retiraron los textos inventados «Esperando respuesta», «Esperando respuesta…» y «Esperando que acepte…».
- «Aceptar y conectar» crea el contacto como `connected`; «Bloquear» lo crea como `blocked`. No hace falta que el remitente envíe ninguna solicitud: su propio mensaje lo es.
- Las conversaciones cuyo contacto está `blocked` se ocultan de la lista.
- **El bloqueo ya es efectivo en el servicio.** `setBlockedPeers` (AIDL → servicio → estado nativo) guarda la lista en `blocked`; `YggdrasilProtocol.handleSignedPacket` descarta antes de descifrar cualquier `HANDSHAKE_*`, `CHAT` o `CHAT_UPDATE` cuyo `senderUpeerId` esté bloqueado, así que no se guarda el mensaje ni se notifica. La lista se sincroniza al guardar o borrar un contacto y al arrancar la sesión (`pushBlockedPeers`). `BlockedPeerList` normaliza y compara sin distinguir mayúsculas, con pruebas Kotlin.
- **Revocación desde el móvil.** `DeviceAuthorizerPanel` muestra un icono de papelera en cada dispositivo que no es el local; al pulsarlo pide confirmación, llama a `revokeAccountDevice(deviceId, 'revocado desde el móvil')`, refresca el registro y deja el aviso «Dispositivo revocado». La lista `Revocados` del propio registro se muestra debajo con la fecha y el motivo, para que la acción sea verificable. `useAccountDevices` expone también `revoked`, `epoch`, `ready` y `localAdmitted`; si el dispositivo local aparece revocado, el panel abre con un aviso de peligro en lugar de la lista normal.
- Purga disponible: `deleteConversation` en AIDL, servicio y TS, con `ProtocolMessageState.withoutConversation` cubierto por pruebas Kotlin.

Queda pendiente:

1. «Eliminar sólo el contacto» como acción separada de «Eliminar contacto y chat» en la ficha.
2. Reflejar en la solicitud el historial previo cuando el contacto se borró pero su conversación no: hoy el cuerpo de la solicitud oculta los mensajes anteriores; decidir si se muestra un resumen o se mantiene la pantalla limpia.
3. Verificar en dispositivo el descarte de paquetes bloqueados con dos nodos reales: la comprobación previa a la firma usa el `senderUpeerId` declarado, que no está autenticado (un tercero solo puede hacerse descartar a sí mismo, pero conviene cubrirlo con una prueba de integración).
4. Un test de integración que recorra el ciclo completo: A escribe a B sin contacto → B ve la solicitud en el cuerpo → acepta → los mensajes siguientes llegan como conversación normal.
5. Propagación de la revocación entre dispositivos propios: el servicio ya rechaza los paquetes del dispositivo revocado (ver 25/10/2026), pero B10 todavía no envía el registro nuevo a los demás dispositivos de la cuenta, así que un dispositivo revocado que siga abierto en otra máquina no se entera de su propio estado.

### Ajustes móviles con paridad de escritorio (24/10/2026)

Los ajustes del móvil se alinean con `src/components/ui/settings` de escritorio. Reglas:

- **Todo panel informativo es un popup, nunca un desplegable dentro de la página.** `InfoDialog` (`src/components/info-dialog.tsx`) es el único componente de ayuda: fondo `theme.backdrop`, tarjeta `theme.backgroundElement` de radio 16, cabecera con icono, título 17/700 y aspa de cierre, y contenido desplazable. Lo usan el icono ⓘ de Perfil y el de Seguridad, de modo que la misma ayuda se ve igual en los dos sitios; antes Seguridad la expandía inline y rompía la gramática.
- **Filas de ajuste compartidas.** `settings-rows.tsx` define las dos únicas filas: `SettingsToggleRow` (64 de alto, título 15/500, descripción 12 en `theme.placeholder` y conmutador propio) y `SettingsInfoRow` (etiqueta a la izquierda, valor a la derecha). El conmutador es propio —pista de 48×28, pulgar de 20— porque el `Switch` de React Native se recorta dentro de contenedores con `overflow: hidden`; el riel usa `theme.accent` encendido y `theme.backgroundSelected` apagado, el pulgar `theme.onAccent`/`theme.background`.
- **Preferencias persistidas en un solo sitio.** `src/domain/preferences.ts` valida el JSON y `src/storage/preferences-store.ts` lo guarda en `expo-sqlite/kv-store` bajo `upeer.preferences`; `usePreferences` es un `useSyncExternalStore`, así que un cambio se propaga a todas las pantallas sin recargar.
- **El tema se resuelve una sola vez.** `useResolvedColorScheme` combina la preferencia (`system`/`light`/`dark`) con el esquema del sistema; `useTheme`, el `TamaguiProvider` de `_layout`, las pestañas, `app-tabs` y `ThemeChrome` consumen ese valor, por lo que el cambio alcanza también la barra de pestañas, la barra de estado y el chrome del sistema.
- **Contenido por sección, portado de escritorio:** Privacidad (lecturas, conectado, última vez), Notificaciones (mensajes, solicitudes, sonido), Apariencia (Sistema/Claro/Oscuro), Bloqueados (lista real de `contact-store` con desbloqueo) y Acerca de (versión, licencia, código abierto, publicidad, datos).
- **El servicio aplica las preferencias de notificaciones.** `YggdrasilTransportService` recibe `setNotificationPrefs` y `setKnownContacts` (AIDL + `AsyncFunction`) y los guarda en `ProtocolStateStore`; `NotificationPrefs` decide con `allows(fromKnownContact)` —contacto conocido usa `mensajes`, desconocido usa `solicitudes`— y `UPeerNotifications` omite la notificación con un log cuando el tipo está apagado. El sonido se resuelve con dos canales, `upeer_messages` y `upeer_messages_silent` (`setSound(null)`), porque `Notification.Builder.setSilent` ya no existe en API 37.
- **Las confirmaciones de lectura sí se aplican.** `setReadReceipts` (AIDL + `AsyncFunction`) guarda el interruptor en `ProtocolStateStore` y la sesión lo reenvía en cada cambio. `YggdrasilProtocol.markConversationRead` pasa los mensajes transicionados por `ReadReceipts.allowed(transitioned, enabled)`: apagado devuelve lista vacía, la conversación queda leída en local y `ControlSender` no envía nada. Al vivir en el servicio, la acción «marcar leído» de la notificación respeta el mismo criterio.
- **La lista de contactos se empuja en cada cambio.** `syncBlockedPeers` envía bloqueados y conocidos juntos, y la sesión reenvía ambos al arrancar, de modo que las preferencias de «solicitudes» solo afectan a remitentes que no están en la agenda.

Queda pendiente en Ajustes:

1. Tamaño de texto (`small`/`medium`/`large`): necesita una escala común aplicada a todas las pantallas, no un valor guardado que nadie lee.
2. Almacenamiento: el móvil no tiene bridge de vault (`getVaultStats`, `cleanupVaultExpired`) ni historial de archivos como `FileHistoryExplorer`.
3. Seguridad de escritorio pendiente de bridge: PIN de bloqueo, revelar la frase con PIN y borrado de cuenta. La lista de dispositivos y la revocación ya son reales.
4. Sección «Perfil» dentro de ajustes: en el móvil el perfil es una pestaña propia, así que no se duplica.

La presencia ya no está en esta lista: «Conectado» y «Última vez» condicionan etiquetas reales derivadas de `lastSeen`.

### Acciones de conversación reales (24/10/2026)

Fijar, silenciar y archivar dejan de ser avisos y pasan a tener efecto:

- **Un solo mapa por conversación.** `src/domain/conversation-flags.ts` define `ConversationFlags` (`archived`, `muted`, `pinned`), `toggleFlag` borra la entrada cuando los tres quedan apagados y `orderByPinned` sube los fijados sin tocar el orden del resto. `parseConversationFlags` descarta identidades vacías y marcas que no sean `true`, así que un JSON corrupto deja la lista sin ajustes en vez de romper Chats.
- **Persistencia sin nativo.** `src/storage/conversation-flags-store.ts` guarda el mapa en `expo-sqlite/kv-store` bajo `upeer.conversationFlags` y expone `useConversationFlags` como `useSyncExternalStore`; el mismo patrón que las preferencias.
- **El silencio llega al servicio.** Al cargar y en cada cambio, el store envía la lista de silenciados con `setMutedPeers`; `ProtocolStateStore` la normaliza con `PeerIdList` y `UPeerNotifications` descarta la notificación con un log. Sin eso, «Silenciar» sería decorativo.
- **Un filtro más, no un estado oculto.** `ChatListFilter` gana `archived`: `Todos` y `No leídos` ocultan lo archivado y `Archivados` muestra solo eso, de modo que nada desaparece sin salida.
- **Las filas dicen su estado.** El item de conversación pinta `Pin`, `BellOff` y `Star` antes del alias y lo anuncia en `accessibilityLabel`, siguiendo la distribución de la lista de escritorio.
- **Favoritos es del contacto, no de la conversación.** Escritorio guarda `isFavorite` en la fila del contacto (`schema.ts`, `setContactFavorite`), así que el móvil hace lo mismo: `contacts` gana `isFavorite` con `ALTER TABLE` guardado por `PRAGMA table_info` —la tabla ya existía en los dispositivos instalados—, `setContactFavorite` lo escribe y `getContacts` traduce el entero a booleano. El chip `Favoritos` filtra por ese dato y la ficha del contacto lo alterna. No se confunde con `pinned`: fijar ordena la lista, favorito solo marca.
- **Las acciones de la ficha dejan de mentir.** «Silenciar», «Archivar» y «Favoritos» salían atenuadas con «Pronto» aunque los datos ya existían desde el menú de Chats; ahora alternan de verdad y cambian su etiqueta al estado contrario. «Cifrado» abre la hoja con el número de seguridad (ver «Detalles de cifrado»).

### Recibos de lectura (24/10/2026)

`READ` deja de ser una fila ausente del inventario:

- **Frame propio, mínimo y firmado.** `ReadReceipts` construye `{ type: "READ", id, timestamp }`, lo firma con `buildSignedPacket` —que añade `senderUpeerId`, `senderYggAddress` y `signature`, y rechaza que el llamante los aporte— y lo envuelve en un `SEALED` con la clave del contacto, igual que hace escritorio en `sendReadReceipt`. Así el recibo no viaja en claro aunque el ACK sí.
- **Se envía al abrir la conversación.** `ProtocolStateStore.markConversationRead` ahora devuelve los `id` que pasaron de `delivered` a `read`; el protocolo los manda en una sola conexión TCP, con tope de 50 por conversación (`ReadReceipts.MAX_PER_CONVERSATION`) para que un hilo largo no abra un envío sin fin. El protocolo guarda el `Bridge` en `start()` porque hasta ahora solo lo pasaba al outbox.
- **Se aplica solo al mensaje propio y del peer correcto.** El `READ` entrante pasa por `ControlReplies` como el `ACK`, verifica la firma contra la clave del remitente —o la del dispositivo si el paquete viene ligado—, se limita a 200/min por origen y por identidad, y usa `ackMessage` con `ReplayPolicy.validAck`, que exige `isMine` y que el dueño del mensaje sea el remitente. Un `READ` de un tercero no puede marcar mis mensajes.
- **La UI ya sabía pintarlo.** El estado `read` sale con el doble check en `theme.receiptRead` en la burbuja y en la lista de chats; lo que faltaba era que el móvil emitiera y aceptara el recibo.

### Indicador de escritura (24/10/2026)

- **Mismo sobre cifrado que el recibo.** `SealedControl.frame` firma con `buildSignedPacket` y sella con la clave del contacto; lo comparten `READ` y `TYPING`, así que un `TYPING` no viaja en claro aunque sea un paquete trivial.
- **El estado caduca solo.** `TypingIndicators` fija una ventana de 6 s y `ProtocolStateStore.typingPeers(now)` poda lo vencido al leer, de modo que un `TYPING` perdido no deja «Escribiendo…» pegado. `ProtocolConversations` añade `typing` a cada conversación, así que la cabecera lo ve con el mismo sondeo de 1,5 s que ya refresca el chat.
- **El emisor no satura.** La pantalla envía como mucho un `TYPING` cada 3 s mientras el campo tiene texto, y el rate limiter lo corta a 600/min por origen y por identidad.
- **Se apaga al llegar el mensaje.** Un `CHAT` recibido limpia el indicador antes de descifrar, para no enseñar «Escribiendo…» junto al mensaje que acaba de entrar.

### Reacciones y borrado de mensaje (24/10/2026)

- **Firma de contenido por actor, no por mensaje.** `ChatReactions` guarda `reactions: { emoji: [actorIds] }` en el propio mensaje y aplica una reacción por autor y emoji, con topes de 20 emojis distintos y 64 autores por emoji; el `CHAT_REACTION` va en el mismo sobre firmado y sellado que `READ` y `TYPING`. El emoji se limita a 16 caracteres.
- **El borrado no confía en el remitente.** `ChatDeletes.deletable` exige que el dueño del mensaje sea quien lo borra y que el mensaje **no** sea local, así que un `CHAT_DELETE` de un contacto solo puede borrar lo que él mismo escribió. Es justo la comprobación que escritorio marca como `// BUG SEGURIDAD` en `handlers/chatEvents.ts`.
- **Borrar es local y explícito.** `ProtocolMessageList.delete`/`deleteLocally` quitan la entrada de la lista en vez de marcarla; el mensaje desaparece del hilo, del último mensaje de la conversación y del contador de no leídos sin dejar hueco.
- **La UI no inventa lenguaje visual.** Pulsación larga sobre cualquier burbuja abre `ChatMessageActions` —misma hoja que el menú de conversación— con seis reacciones rápidas, copiar y «Eliminar para todos»; las reacciones se pintan como fichas bajo la burbuja (`ChatReactionRow`), alineadas al lado del mensaje y en `theme.accent` cuando la reacción es propia. Editar sigue en el mismo menú para los mensajes propios.
- **`ProtocolStateStore` dejó de crecer.** La lista de mensajes se extrajo a `ProtocolMessageList.kt`, las preferencias (bloqueados, conocidos, silenciados, notificaciones y confirmaciones de lectura) a `ProtocolPreferences.kt` y los envíos de control a `ControlSender.kt`; el store queda en 262 líneas, `YggdrasilProtocol` en 292 y no hay archivos de reexportación.

### Tarjetas de contacto (24/10/2026)

- **No hay paquete nuevo, y es a propósito.** Escritorio declara `CHAT_CONTACT` en el router y tiene handler, pero **ningún sitio lo emite**: `sendContactCard` en `chatInteractions.ts` manda un `CHAT` normal cuyo cuerpo es el JSON de la tarjeta y su renderer lo parsea en `messageItemSupport.ts`. Añadir el paquete en Android sería código que nadie ejerce y que además exigiría otra regla de rate limit y otra rama de admisión. Se implementa como **contenido de `CHAT`**, así que funciona en los dos sentidos con el código que ya existía: outbox, ACK, deduplicación por UUID, RECIBOS y notificaciones son los de un mensaje más.
- **El formato es el de escritorio, sin inventar campos.** `serializeContactCard` emite `{ type: "contact_card", text: "", contact: { name, address, upeerId, publicKey, avatar } }`, exactamente lo que produce `serializeContactCardMessage` en `handlers/chatContact.ts`, de modo que una tarjeta del móvil se pinta en escritorio y viceversa.
- **Se valida al parsear, no al confiar.** `parseContactCard` exige `type`, un `upeerId` de 64 hex y recorta nombre y dirección a 100 caracteres; descarta el `publicKey` si no mide 64 y el `avatar` si no es `data:image/` o pasa de 64 KB. Ese tope no es arbitrario: `YggdrasilOutbox` rechaza cualquier `CHAT` de más de 256 KB, así que un avatar enorme haría la tarjeta inenviable y engordaría el estado cifrado. Lo que no parsea se pinta como texto normal, sin romper el hilo.
- **La lista de chats no enseña JSON.** `messageSummary` convierte la tarjeta en «Tarjeta de contacto: <nombre>» en la vista previa, igual que el `getContactCardSummary` de escritorio.
- **Añadir desde la tarjeta es explícito.** Pulsar una tarjeta de alguien que no está en la agenda pide confirmación, guarda el contacto con `status: 'pending'` y lo registra en el servicio nativo con `registerPeer`, los mismos dos pasos que el alta manual. Si ya existe, abre su ficha. No se acepta sola ni se salta la solicitud de mensaje: eso sigue siendo cosa del primer contacto.
- **Compartir es una hoja, no una pantalla.** La fila «Compartir contacto» de la ficha abre `ContactShareSheet` con el mismo buscador de contactos que «Nueva conversación» (`ContactPicker`, extraído para no duplicar la lista), excluyendo al propio contacto.
- **La tarjeta del hilo y la ficha replican escritorio.** La burbuja (`ChatContactCard`) pinta avatar de 44, nombre, «Tarjeta de contacto», el pie «Toca para ver y guardar» / «En tu agenda» y un chevron, sobre `theme.background` con borde de un hairline. Al pulsarla se abre `ContactCardSheet`, que copia `ContactCard.tsx`: avatar de 56, nombre, «Tarjeta de contacto», la identidad `upeerId@address` en monoespaciada sobre una caja tintada, el QR de 180 dentro de una caja blanca con padding y un botón contorneado a ancho completo. La etiqueta del botón sigue la misma matriz que escritorio: «Guardando…», «Eres tú», «Contacto bloqueado», «Solicitud pendiente», «Ya guardado» o «Guardar contacto».

### Responder a un mensaje (24/10/2026)

- **El campo ya existía en el cable.** Escritorio mete `replyTo` (UUID del mensaje citado, ≤100) como campo de primer nivel del paquete `CHAT` en `chatDirectDelivery.ts`, y el crate lo firma con el resto. Android ya lo enviaba: `OutboxStore`, `OutboxPacketFactory` y `buildChat`/`buildLinkedChat` lo arrastraban desde el primer día. Lo que faltaba era recibirlo, guardarlo y pintarlo.
- **Entrada validada antes de guardar.** `YggdrasilChatDecryptor` solo persiste `replyTo` si `ReplayPolicy.validMessageID` lo acepta, de modo que un paquete con basura no ensucia el estado. En salida, `ChatSendPolicy.text` concentra las cuatro comprobaciones de envío (id de 64 hex, cita UUID o vacía, texto no vacío y tope de 256 KB) y es lo que prueba `ChatSendPolicyTest`.
- **La cita se resuelve en local, no viaja copia del texto.** `buildReplyPreview` busca el mensaje citado entre los cargados y produce `{ author, excerpt }`; si ya no está (historial purgado o mensaje borrado) no se pinta nada, en vez de inventar «mensaje no disponible». `replyExcerpt` colapsa espacios, recorta a 80 caracteres y resume las tarjetas de contacto con el mismo `messageSummary` que la lista de chats.
- **Un solo banner para los dos contextos.** `ChatComposerArea` sustituye al bloque de edición que vivía dentro de la pantalla y muestra «Editando mensaje» o «Respondiendo a <alias>» con el extracto y una X para cancelar; editar y responder son excluyentes y `submit` limpia ambos. La pantalla baja a 292 líneas porque la gestión de la hoja de acciones se movió a `useChatMessageSheet`.
- **La cita se lee dentro de la burbuja.** `ChatReplyQuote` copia el `MessageReply` de escritorio: barra de acento de 3 px a la izquierda (`theme.bubbleMineText` en mensajes propios, `theme.accent` en ajenos), fondo translúcido del propio tono, autor a 11/700 y extracto a 12 con opacidad 0.85, **sin `lineHeight`** —fijarlo es lo que convertía la cita en un bloque enorme y desalineado de la altura real del texto— y la burbuja pasa a columna cuando hay cita para que no compita con el texto.
- **La cita es navegable.** Tocar la cita llama a `openQuoted`, que busca el original en la lista cargada y hace `scrollToIndex`; si el original ya no está en memoria no se hace nada, igual que en escritorio. Se implementa con `onResponderRelease` en vez de `Pressable` para no meter un contenedor táctil dentro de otro y romper el long-press que abre las acciones.
- **El banner del compositor tiene la misma gramática.** `ChatComposerArea` pinta borde izquierdo de acento de 3 px, «Respondiendo a <alias>» a 11/700 en acento y el extracto a 12/0.85, sin `lineHeight`.


### Reenviar a otros contactos (25/10/2026)

Escritorio tenía `ForwardModal` y `forwardMessageToTargets`; el móvil no ofrecía nada y el roadmap lo daba por pendiente.

- **Reenviar es enviar con otro destinatario, no un paquete nuevo.** Escritorio reenvía el `content` tal cual a cada destino (`sendMessage(target, payload.content, …)`), sin marca de «reenviado» en el cable. El móvil hace lo mismo con `sendChat(upeerId, content, '')`: sin cita, porque el original no viaja y citar un mensaje que el receptor no tiene produciría una cita vacía.
- **Selección múltiple, como escritorio.** `ChatForwardSheet` monta el `ContactPicker` de siempre en modo selección: dos props opcionales (`onToggle` y `selectedIds`) convierten las filas en casillas con check de acento y `accessibilityState.selected`, sin romper los dos usos que ya había (compartir tarjeta y nueva conversación).
- **El resumen es una función pura.** `src/domain/forward.ts` concentra `forwardExcerpt` (colapsa espacios y recorta a 120) y `forwardResultMessage` (singular, plural, fallos parciales y «no salió ninguno»), con `forward.test.ts`. La hoja solo pinta.
- **Los fallos no abortan el resto.** Cada destino se envía en su propio `try`: si uno falla, los demás siguen y el snackbar dice cuántos salieron y cuántos no.
- **La pantalla de chat no crecía.** `ChatMessageOverlays` agrupa la hoja de acciones y la de reenvío, de modo que `[upeerId].tsx` quedó en 292 líneas con cuatro hojas ya encima de la mesa.

### Presencia real y última vez (25/10/2026)

La cabecera decía «En línea» siempre porque el estado salía de la agenda (`status === 'connected'`), no del tráfico. Ya hay presencia real:

- **El servicio fecha cada paquete verificado.** `ProtocolStateStore.touchPresence(upeerId)` reescribe `lastSeen` del peer y se llama en `YggdrasilProtocol.handleChat`, `handleChatUpdate` y en `ControlReplies.handle` —después de validar la firma y el rate limit—, de modo que `CHAT`, `CHAT_UPDATE`, `ACK`, `READ`, `TYPING`, `CHAT_REACTION` y `CHAT_DELETE` cuentan como actividad. Los handshakes ya lo hacían vía `rememberPeer`.
- **La presencia viaja en el payload que ya existía.** `ProtocolConversations.build` añade `lastSeen` a cada conversación, así que la pantalla de chat —que ya refresca cada 1,5 s— y la lista de Chats la reciben sin un canal nuevo. `ProtocolConversationsTest` cubre el paso del dato y el cero cuando el peer no está en el registro.
- **La ventana es la de escritorio.** `src/domain/presence.ts` define `ONLINE_WINDOW_MS = 65_000`, `isOnline` y `describeLastSeen`, con el mismo texto que `topHeaderSupport` de escritorio: `En línea`, `Última vez hace un momento`, `Última vez hace 5 min`, `Última vez hoy a las 09:00`, `Última vez el 21 oct a las 18:30`. `presence.test.ts` fija los casos.
- **Las preferencias deciden qué se enseña.** `usePresenceLabel(lastSeen)` cruza `onlineStatus` y `lastSeen` con la hora actual y refresca cada 30 s para que el «En línea» caduque solo. Con «Conectado» apagado se ve la última vez; con «Última vez» apagado, `Desconectado`; con ambos, nada se inventa.
- **Se aplica en los dos sitios que lo mostraban mal.** `ChatHeader` recibe `presence` (y sigue priorizando `Escribiendo…`), y `ContactInfoHero` también, con el punto en verde solo cuando la etiqueta es `En línea`. La ficha del contacto pide `lastSeen` en su carga junto a la agenda.
- **La lista de Chats usa la misma regla.** `src/domain/chat-preview.ts` decide la segunda línea de cada fila con prioridad `pendiente` → `Escribiendo…` (en acento y con la hora en negrita, como escritorio) → vista previa del último mensaje → presencia cuando la conversación aún no tiene mensajes. `ChatListItem` recibe `now` desde un único `useNow(30 s)` en la pantalla para no crear un temporizador por fila, y el rótulo de accesibilidad anuncia «escribiendo».

### Detalles de cifrado y número de seguridad (26/10/2026)

La fila «Cifrado» de la ficha estaba atenuada con «Pronto» porque el móvil no exponía nada de la sesión. Ahora enseña lo mismo que `ContactInfoCipherView.tsx` de escritorio, con un dato verificable de verdad.

- **El número de seguridad se calcula en Kotlin.** `SafetyNumber.compute` ordena las dos claves públicas, las concatena, hace SHA-256, recorta a 20 bytes y agrupa en 8 bloques de 5 hexadecimales en mayúsculas (`EA20E 617CA F3AF1 304D8 F45B5 73B9C 4AB7C B37DE`). Es simétrico —los dos lados obtienen el mismo número— y valida que ambas entradas sean 64 hex. `SafetyNumberTest` fija seis vectores calculados fuera del proyecto.
- **El dato viaja por la cadena que ya existía.** `YggdrasilProtocol.peerSecurityJSON` responde con `hasSession` y `safetyNumber` a través de `IYggdrasilTransport.aidl` → `YggdrasilTransportService` → `UPeerTransportModule` → `getPeerSecurity` en TypeScript, con stub en `.web.ts` y el tipo `PeerSecurity`.
- **La hoja replica los tres puntos de escritorio.** `ChatCipherSheet` pinta «Nadie puede espiar», «Solo entre vosotros dos» y «Puedes comprobarlo» con el mismo icono en caja tintada, el candado de éxito, el estado de la sesión y el número de seguridad en un `CopyField` que copia al pulsar. El número va sin truncar: se parte en dos líneas para poder leerlo entero, que es justo para lo que sirve.
- **Sin «Verificar identidad».** Escritorio tiene ese botón sin cablear; el móvil no lo pinta para no prometer un flujo que no existe.
- **Los dos algoritmos ya coinciden.** Escritorio gana `computeSafetyNumber` (`src/main_process/security/safetyNumber.ts`), copia exacta de `SafetyNumber.compute`: ordena las dos claves públicas, concatena y hace SHA-256 de 20 bytes en 8 grupos de 5. `safetyNumber.test.ts` reutiliza **los mismos vectores** que `SafetyNumberTest` del móvil, así que un cambio de formato en un lado rompe las pruebas del otro. El IPC `get-contact-security` devuelve `hasSession` y el número, y `ContactInfoCipherView` los pinta con copia al pulsar, igual que la hoja del móvil.
- **`computeKeyFingerprint` sigue existiendo y no es lo mismo.** Es BLAKE2b-128 de **una** clave y solo se usa en los avisos TOFU de «la clave de este contacto ha cambiado» (`contactsHandshakeAccept`, `contactsHandshakeReq`); ahí interesa identificar la clave concreta, no un número simétrico entre dos partes. Confundir ambas funciones fue justo lo que hizo parecer irresoluble el problema.
- **El hook es estable.** `usePeerSecurity` expone `load`, `reset` y `state`; `load` se extrae antes de usarse como dependencia del efecto, porque un objeto de hook en las deps de un `useEffect` cambia de identidad en cada render y provoca «Maximum update depth exceeded».

### Lápidas, hora en las citas y presencia con PING (26/10/2026)

Tres fallos vistos en vivo con los dos clientes conectados: el mensaje borrado desaparecía en el móvil, la hora de un mensaje con cita se caía a una línea propia y escritorio marcaba «última vez hace 4 minutos» a un móvil que estaba alcanzable.

- **Borrar deja lápida, no borra.** `ProtocolMessageState.markDeleted` escribe `content = "Mensaje eliminado"`, `deleted`, `deletedAt`, quita `replyTo` y `reactions` y pone `isEdited = false`; `ProtocolMessageList.delete`/`deleteLocally` lo aplican y `remove()` desapareció. Una lápida no se vuelve a borrar ni acepta `applyContent`. `ProtocolConversations` no la cuenta como no leída.
- **La píldora de reacciones se pega un poco más a la burbuja.** `ChatReactionRow` pasa de `marginTop: 2` a `marginTop: -2` (4 dp ≈ 11 px), así que la píldora deja de flotar separada del mensaje y apoya casi en su borde inferior, que es lo que hace escritorio con `bottom` negativo. Se probó `-3` y tocaba la burbuja: `-2` deja un hilo de aire. Cuidado al validar cambios de estilo en el móvil: `adb shell am start` con un deep link **no** recarga el bundle; hay que forzar `Page.reload` por CDP contra el inspector de Metro, y hasta entonces la captura sigue mostrando el estilo viejo.
- **La burbuja gana un píxel de aire.** El texto quedaba pegado al borde superior e inferior de la píldora, así que las burbujas de una sola línea se leían como un bloque macizo. `styles.bubble` pasa de `paddingTop 4`/`paddingBottom 2` a `5`/`3`; es la única desviación deliberada respecto a `MessageItem.tsx` de escritorio (`p: 0.5`, `pt: 0.5`, `pb: 0.25`), pedida a propósito para el móvil y de un solo píxel por lado.
- **La burbuja pinta la lápida.** `ChatMessageBubble` lee `deleted`, enseña el texto en cursiva con opacidad 0.6, oculta la cita, la tarjeta y «editado», y `ChatMessageActions` deja de ofrecer editar, reenviar y reintentar. Es exactamente lo que hace `MessageItem.tsx`: `opacity 0.6`, `fontStyle italic`, sin cita ni adjuntos, `canEdit` falso.
- **La hora vuelve a la fila de metadatos.** La cita estaba *dentro* de la fila `body` junto al texto y el bloque de metadatos, así que con cita el contenedor pasaba a columna y la hora caía debajo. Ahora la cita es un bloque propio a ancho completo (`styles.quote`) y `body` sigue siendo una fila con el texto y `meta` alineado a la derecha. Es la maquetación de escritorio: cita arriba, texto y hora en la misma línea.
- **El PING no se contestaba.** El heartbeat de escritorio manda `PING` cada ~10 s a los contactos de las últimas 24 h, y el móvil lo rechazaba con «unsupported packet type PING», así que escritorio solo refrescaba `lastSeen` con tráfico real y a los cuatro minutos de inactividad daba al móvil por desconectado. Ahora `PING` entra por `ControlReplies`, cuenta como presencia (`touchPresence`) y responde `PONG` con `ControlPings.pong()` y `ControlSender.sendPong`, en un hilo propio como el resto de controles. El rate limit del móvil gana la regla `PING` (60 por 10 s, la misma que escritorio).
- **Verificado en vivo:** con el móvil parado más de 85 s, escritorio sigue mostrando «En línea». La ficha de contacto de escritorio deja de contradecirse: la tarjeta «Última Actividad» escribe «Ahora mismo» cuando el contacto está en línea en lugar de una hora relativa vieja.

### Presencia = actividad del usuario, no del nodo (26/10/2026)

Decisión de producto: **«En línea» significa que la persona ha estado activa, no que su nodo esté encendido.** Que el servicio, el daemon o la VPN del par estén levantados es un detalle técnico de transporte; el usuario no tiene por qué saber que existen y no debe verlo traducido a un punto verde. La entrada anterior del `PING` queda así **matizada**: aquel arreglo evitaba que escritorio diera por desconectado un móvil alcanzable, pero un nodo que contesta no es una persona delante de la pantalla.

- **Qué mide hoy `lastSeen`, y por qué no sirve.** `ProtocolStateStore.touchPresence` se ejecuta en cualquier paquete entrante autenticado: `CHAT`, `CHAT_UPDATE`, `ACK`, `DR_RESET` y también el `PING` del heartbeat de escritorio, que llega cada ~10 s. `ONLINE_WINDOW_MS = 65_000` sobre ese valor mide **alcanzabilidad**: un móvil con la app cerrada pero el servicio vivo contesta `PONG` y se queda «En línea» de forma indefinida.
- **Dos datos, no uno.** `lastSeen` se queda como está, en el servicio, para diagnóstico y para el reintento/reachability del outbox. La etiqueta del usuario pasa a derivarse de `lastActive`, que **solo avanza con actividad humana real**: abrir la app, volver a primer plano, abrir una conversación, escribir, enviar, leer o reaccionar. El `PING`/`PONG`, los ACK, los `DR_RESET` y cualquier tráfico de protocolo **no** tocan `lastActive`.
- **La ventana no cambia, el dato sí.** `ONLINE_WINDOW_MS` se aplica a `lastActive`: dentro de 65 s es «En línea»; después, «Última vez …» con la hora de la última acción real. `describePresence` no necesita cambios; cambia de dónde sale su entrada.
- **Nada de estados técnicos en la UI.** No se expone «nodo conectado», «servicio activo» ni equivalentes en la lista, la cabecera ni la ficha del contacto. El estado de red y DHT sigue viviendo solo en Ajustes › Diagnóstico.
- **Implementado (26/10/2026).** Lo que sigue es lo que quedó en el código, no el plan:
  1. `ProtocolPresence.kt` guarda `lastActive` por peer con `touch()`, separado de `touchPresence`. `ProtocolTyping.kt` se extrajo de `ProtocolStateStore` para no pasar de 300 líneas.
  2. `ControlSender.markActive()` es la única puerta de entrada, con `AtomicLong`, throttle de 20 s y ventana de 5 min; nada de protocolo lo llama. El `PING` que manda el móvil lleva `activeAt`, y `ControlReplies` lo persiste al recibirlo del par; el `PONG` sigue sin payload de actividad.
  3. `lastActive` viaja en el payload de `ProtocolConversations` y sube por AIDL con `markUserActive`. `use-activity-signal.ts` marca actividad al montar y cada vez que `AppState` vuelve a `active`.
  4. `presence.activeAt()` prefiere `lastActive` y cae a `lastSeen` solo si nunca hubo actividad, así que `describePresence` no cambió. `use-presence-label.ts` pasa por ahí.
  5. Pruebas: `ProtocolPresenceTest.kt` (6 casos, incluido que `PING` no mueve `lastActive`) y `presence.test.ts` con el caso «el nodo contestó pero no hubo actividad → `Última vez`».
  6. Ajustes › Privacidad sigue mandando: «Conectado» apagado oculta la etiqueta en línea; «Última vez» apagado oculta la hora.

### Presencia desde el móvil: por qué escritorio no se entera (26/10/2026)

Diagnóstico pedido tras observar que navegar conversaciones en el móvil no ponía al contacto «En línea» en escritorio. La etiqueta se calcula bien en los dos lados; **el móvil casi nunca emite el dato**. Cuatro causas, comprobadas en código:

1. **El hook móvil no escucha gestos.** `src/hooks/use-activity-signal.ts` llama a `markUserActive` al montar y cuando `AppState` pasa a `active`, y nada más. El equivalente de escritorio (`src/hooks/useActivitySignal.ts`) escucha `pointerdown`, `keydown`, `focus` y `visibilitychange` con throttle de 15 s. En el móvil, tocar, navegar y escribir no producen ni un `activeAt`.
2. **Nadie repite el anuncio.** `ControlSender.markActive` tiene throttle de 20 s (`ACTIVE_INTERVAL_MS`) pero **no temporizador**: es «una vez por vuelta a primer plano», no un latido. Escritorio sí reanuncia cada 10 s (`appInitializerSupport.ts` → `checkHeartbeat` → `heartbeat.ts` con `activeAt: localActiveAt()`).
3. **La ventana de destinatarios es de 5 minutos.** `markActive` envía sólo a `presence.targets(now - ACTIVE_TARGET_WINDOW_MS, MAX_ACTIVE_TARGETS)` y `ProtocolPresence.targets` descarta todo peer con `lastSeen` anterior a esa marca. Abrir una conversación con alguien a quien el nodo no ha oído en 5 minutos devuelve lista vacía y **no sale ni un paquete**.
4. **La ventana de «En línea» de escritorio es de 65 s.** `useIsOnline.ts` (`DEFAULT_ONLINE_WINDOW_MS = 65_000`). Aunque el `PING` salga, caduca enseguida y no hay reemplazo hasta la siguiente vuelta a primer plano.

A esto se suma que escritorio acepta `activeAt` en **cualquier** paquete (todo lo que no sea `CALL_MEDIA`), pero en el móvil `activeAt` sólo aparece en `ControlPings.kt` y `ControlReplies.kt`: enviar mensajes tampoco refresca la presencia vista desde escritorio.

La dirección inversa sí funciona: el `PING` de escritorio llega cada ~10 s y el móvil lo persiste con `touch`.

Arreglo, pendiente en parte. **No bloquea nada**, así que no entra en el orden de ejecución por bloqueos: viaja con el paso 7 (cierre de UI) y puede tomarse antes en cualquier momento porque es barato.

1. **[Hecho, 26/10/2026] Hook con gestos además de primer plano.** `src/domain/activity-signal.ts` concentra la regla pura —`shouldReportActivity(trigger, lastReportedAt, now)` con `ACTIVITY_THROTTLE_MS = 15_000` y `ACTIVITY_HEARTBEAT_MS = 40_000`, y `resume` siempre reporta— y `use-activity-signal.ts` la aplica devolviendo `{ reportTouch }`. `session-shell.tsx` envuelve los hijos en un `View` con `onStartShouldSetResponderCapture` que reporta y devuelve `false`, así que ningún toque se pierde y ninguna pulsación se consume. El latido de 40 s corre mientras `AppState === 'active'`. 8 casos unitarios. **Sin probar todavía en dispositivo ni cruzado con escritorio.**
2. **[Hecho, 26/10/2026] Latido mientras la app está en primer plano cada ~40 s** (ver arriba), para no depender de gestos: 65 s de ventana deja margen.
3. Pendiente. `targets` deja de exigir `lastSeen` reciente: interlocutores con dirección y clave conocidas, ordenados por recencia, y sin recortar a 20 si eso deja fuera conversaciones abiertas. Es el punto que de verdad rompe el caso «abro una conversación con alguien a quien no oigo desde hace rato», y el gesto por sí solo no lo arregla.
4. Pendiente. `activeAt` en los paquetes salientes del móvil, `CHAT` incluido, para que escritorio refresque aunque el `PING` no llegue a salir.
5. Pendiente. Prueba cruzada de presencia dentro de la prueba real Desktop↔móvil.

### Buscar en la conversación (26/10/2026)

Escritorio tiene búsqueda dentro del chat; el móvil no tenía nada. La primera versión fue una hoja inferior con input y lista, y el veredicto fue claro: **no se quiere una hoja**. Ahora el buscador sustituye a la barra de texto mientras está abierto, como hace WhatsApp.

- **La barra ocupa el sitio del compositor.** `ChatInputArea` decide entre `ChatComposerArea` y `ChatSearchBar`: al abrir desde el menú de la cabecera la barra de escribir desaparece y en su lugar queda X, campo con lupa, contador y flechas arriba/abajo. Al cerrar vuelve el compositor con el texto que hubiera.
- **El contador dice dónde estás.** `describeSearchPosition(index, count)` escribe `1 de 3` o `Sin resultados` y acota índices fuera de rango; las flechas se deshabilitan sin coincidencias. `describeSearchResults` se retiró al quedarse sin uso con la hoja.
- **La coincidencia se ve en el hilo, no en una lista.** Al escribir se salta a la coincidencia más reciente y la burbuja se marca con borde de acento (`highlighted` en `ChatMessageBubble`). Las flechas recorren las coincidencias en bucle reutilizando `openQuoted`, que ya hacía scroll por id.
- **La búsqueda vive en un hook.** `useChatSearch` guarda `active`, `query`, `index` y las coincidencias, y expone `open`, `close`, `changeQuery` y `step`; la pantalla de chat se queda en 299 líneas. `searchChatMessages` sigue en `domain/chat-search.ts` (normaliza acentos, últimos 50, más reciente primero) con 9 pruebas.
- **El scroll lejano ya no se pierde.** La lista pasa `onScrollToIndexFailed`: estima el offset con el alto medio y reintenta el `scrollToIndex` 80 ms después, así saltar a un mensaje que aún no estaba renderizado termina llegando.

### Reintento de mensajes y qué está probado de verdad (26/10/2026)

La pregunta era si el reintento estaba bien implementado y si se habían probado todos los tipos de mensaje online y offline. Respuesta corta: **el reintento de texto sí existe y funciona, tenía dos agujeros, y «todos los tipos» no es cierto en el móvil porque adjuntos no hay.**

**Lo que sí está.** `YggdrasilOutbox` es una cola persistente (`OutboxStore`, cifrada con Keystore) con hilo propio cada 1,5 s, 8 envíos en paralelo, 12 hilos y ACK por mensaje. Al enviar se guarda el paquete cifrado, de modo que un reenvío no vuelve a tocar el ratchet; el ACK borra la entrada; `onPeerReset` y `onHandshakeAccepted` reactivan las pendientes tras un `DR_RESET` o un handshake nuevo. Los errores duros (peer desconocido, sin dirección, handshake pendiente, admisión de contacto, `writeFrame` fallido) se reintentan con espera creciente.

**Agujero 1 — un envío sin ACK se reenviaba para siempre.** `markSent` programaba el siguiente intento a +5 s pero no contaba nada, así que un peer alcanzable que nunca confirma recibía el mismo paquete cada 5 s de forma indefinida: ni `failed`, ni fin. Ahora `OutboxStore` lleva un contador `sends` y `OutboxRetryPolicy.canResend` corta a los 8 reenvíos sin confirmación; al agotarse la entrada se borra y el mensaje pasa a `failed`. Los errores duros conservan su límite de 30 intentos, que es distinto a propósito: un fallo de red puede durar horas, una falta de ACK con el peer vivo es otra cosa.

**Agujero 2 — un mensaje fallido era un callejón sin salida.** No había forma de reintentarlo sin copiar el texto a mano. Ahora `retryMessage` va de la hoja de acciones a `retryChat` (AIDL → `YggdrasilTransportService` → `YggdrasilProtocol` → `YggdrasilOutbox`), que valida que el mensaje sea propio, de esa conversación y esté en `failed`, lo reencola con el mismo id y lo devuelve a `pending`. La fila «Reintentar envío» solo aparece en mensajes propios fallidos, como el icono de escritorio de `MessageStatus`.

**La política es un objeto puro.** `OutboxRetryPolicy` (espera acotada, 30 intentos duros, 8 reenvíos sin confirmación) con `OutboxRetryPolicyTest` de 4 casos, porque el outbox real no se puede probar sin Keystore.

**Verificado en el dispositivo.** Con dos mensajes reales en `failed`, `retryChat` devolvió `true`, el log nativo escribió «reencolado» y las burbujas volvieron a `pending` al instante. La hoja de acciones muestra «Reintentar envío» encima de «Editar mensaje».

**Lo que sigue sin probarse, y hay que decirlo:**

- **Adjuntos, imágenes, vídeo, audio y documentos no existen en el móvil**: no hay ni un `FILE_*`. La prueba online/offline con archivos solo existe en escritorio.
- **No hay prueba automática del outbox móvil de extremo a extremo.** Lo cubierto son las piezas puras; el camino completo se ha visto a mano.
- **Escritorio reintenta en un solo disparador.** `chatRetry.registerPendingDirectMessage` guarda en memoria (no en disco) y `retryPendingDirectMessages` solo se llama desde `drResetRoute`. El mensaje offline normal no depende de eso —lo cubre el fallback a vault a los 2,5 s y la `pending_outbox` en SQLite—, pero un reinicio de la app pierde esa cola en memoria.
- **Los `vaulted` móviles no existen**: sin vault, el móvil no tiene el equivalente al «guardado para entregar después» de escritorio, así que un contacto offline acaba en `failed` y depende del reintento manual.

### Tamaño del texto con escala común (26/10/2026)

La fila «Tamaño del texto» no existía: solo había una nota de que se aplicaría «cuando todas las pantallas usen la escala común». Ahora hay escala, selector y superficies reales.

- **La escala es una función pura.** `scaleFontSize(size, opción)` aplica el factor de `TEXT_SCALE_FACTORS` (pequeño 0,9 · normal 1 · grande 1,18), redondea a píxel entero y nunca baja de 9 px, que es el mínimo legible por debajo del cual ya no se puede recortar nada. `text-scale.test.ts` fija identidad en normal, ambos extremos, redondeo, mínimo y orden entre escalas.
- **Un solo hook para todas las superficies.** `useTextScale()` devuelve `scale(n)` derivado de la preferencia `fontSize` que ya vivía en `upeer.preferences`; ningún componente guarda estado propio ni duplica factores.
- **Escalado donde se lee:** mensaje (contenido, «editado» y hora), cita de respuesta (autor y extracto), fila de Chats (título, hora, vista previa y contador de no leídos) y cabecera del chat (alias y subtítulo). Iconos, avatares y espaciados no cambian: solo el texto.
- **El selector es su propia muestra.** Tres pastillas con «Aa» dibujada en 13/16/20 px y la etiqueta debajo; la activa se pinta con el acento y expone `accessibilityState`. Debajo se lee el porcentaje (`Tamaño normal`, `Texto al 90 %`, `Texto al 118 %`).
- **Verificado con la preferencia persistida**, no solo con el valor por defecto: sembrando `fontSize: "large"` en `upeer.preferences` y relanzando la app en frío, el mensaje sube a 17 px, la cita a 14 y el alias de la cabecera trunca antes; luego se devolvió a `medium`.
- **El resto de pantallas ya usa la misma escala.** Ajustes entero (raíz y secciones de Red, Privacidad, Notificaciones, Seguridad, Apariencia, Bloqueados y Acerca de), Contactos, ficha del contacto y Perfil. `settings-section-parts.tsx` centraliza el encabezado y la nota de sección, que antes repetían el mismo `Card` cinco veces; `settings-rows.tsx` hace lo propio con las filas de interruptor e información. `TextSmall` de la sección de Red construye también su `lineHeight` con `scale()`, no solo el `fontSize`.
- **Reverificado en pantalla con `large`**: Red, Contactos y ficha del contacto. Dos notas mentían y se corrigieron en el mismo paso: Privacidad afirmaba que la presencia «se aplicará cuando el protocolo transporte presencia» (ya existe) y Notificaciones decía que «se enviarán con el próximo trabajo» (ya se aplican).
- **Segunda pasada: las hojas y los flujos.** La escala llega ya a todo lo que se abre desde el chat y desde Ajustes: menú contextual de conversación, acciones del mensaje, reenvío, tarjeta de contacto, cita del compositor, barra de búsqueda, filas de reacción, estado vacío, pantalla de solicitud, menú de la cabecera, cifrado con número de seguridad, escáner QR, formulario de alta, selector de contactos, nueva conversación, invitación de dispositivo, emparejamiento, panel de dispositivos, Perfil (hero, reputación, dirección, campos técnicos y QR), Apariencia, Chats, Conexión, arranque y autenticación. `snackbar`, `bottom-sheet` e `info-dialog` también escalan, así que el aviso de copia y cualquier hoja heredan el tamaño.
- **Dos excepciones a propósito.** El glifo del emoji del menú de reacciones (`fontSize={20}`) no escala: es un icono, no texto. Y `network-map` conserva sus tamaños fijos porque dibuja sobre un `Svg` con posiciones absolutas donde el texto no puede crecer sin romper la composición.
- **Verificado con `large` en Chats, Perfil, Apariencia y ficha del contacto**, y luego devuelto a `medium`.

### Revocación efectiva de dispositivos (25/10/2026)

El registro firmado ya guardaba los revocados, pero revocar no cambiaba nada en el servicio: ni se purgaban los datos del dispositivo, ni se descartaban sus paquetes.

- **El servicio conoce los revocados.** `AccountRegistryStore.revokedDevices()` publica la lista del registro y `YggdrasilTransportService.revokedDevicesOf(account)` decide de qué registro sale: el propio si la cuenta es la local, el de contactos si no. Esa función es la que alimenta `DeviceMessagingBindings.revokedFor`.
- **Se descarta antes de descifrar.** `DeviceMessagingBindings.parse` rechaza un handshake cuyo `deviceId` esté revocado, y `YggdrasilProtocol.handleSignedPacket` devuelve `rejectedMetadata(packet)` —el mismo marcador silencioso que `blockedMetadata`— para `HANDSHAKE_*` con identidad inválida o revocada y para `CHAT`/`CHAT_UPDATE` cuyo `senderDeviceId` esté revocado. Ni se guarda, ni se notifica, ni se confirma.
- **La purga es local y aditiva.** `DevicePurge.apply(state, deviceId)` (función pura sobre el `JSONObject` del estado, con `DevicePurgeTest`) borra `devicePublicKey`, `deviceId`, `deviceCertificate` y `deviceSignedPreKey` de cualquier peer cuyo `deviceId` coincida sin distinguir mayúsculas, y tira su sesión. **Conserva `publicKey`**, así que un emparejamiento posterior vuelve a funcionar sin perder el contacto.
- **El callback dice qué dispositivo.** `onRevokedDevice` pasa de `() -> Unit` a `(String) -> Unit`; el servicio llama a `forgetRevokedDevice(deviceId)`, que purga el estado y revalida la admisión con `admissionGuard.isAdmitted()`.
- **La UI no miente.** `useAccountDevices` expone `localAdmitted`, `ready` y `epoch`; el panel enseña un aviso de peligro si el dispositivo local está revocado, el motivo y la fecha de cada revocación, el `epoch` en el aviso de éxito, y un mensaje distinto cuando el registro aún no está listo en vez de una lista vacía.
- **El dispositivo revocado ya tiene salida.** El aviso de peligro del panel ofrece «Volver a vincular este dispositivo» y abre el mismo `DevicePairingPanel` que la pantalla de recuperación, dentro de una hoja inferior: `deviceEnrollmentBegin` genera una clave de dispositivo nueva, así que el emparejamiento sustituye la identidad en lugar de reutilizarla. Tras vincularlo hay que cerrar y abrir la sesión con la frase.
- Pendiente: **propagar el registro nuevo entre dispositivos propios** (B10) para que un dispositivo revocado que siga encendido en otra máquina se entere; hoy solo lo aprende quien recibe su tráfico.

### Gestión de dispositivos en escritorio: sección propia (26/10/2026)

El emparejamiento estaba enterrado en **Seguridad**, entre el PIN, la frase de recuperación y la zona de peligro, dentro de una tarjeta con `borderRadius: 16px` que rompía la convención visual de la sección (cabecera con icono, rótulo en versalitas y `borderBottom: 1px divider`). Nadie lo encontraba: es una acción de alta, no de protección.

Se añade una sección **Dispositivos** en el rail de Ajustes, entre Perfil y Privacidad, con tres bloques y el mismo estilo plano que el resto:

- **Dispositivos de la cuenta** — qué significa compartir cuenta y que ningún dispositivo recibe la semilla.
- **Vincular un dispositivo nuevo** — la invitación, el código de solicitud y la respuesta.
- **Dispositivos autorizados** — el registro firmado, con `epoch` y revocación.

Además se resuelve la duplicidad que generaba la confusión: `DeviceSessionList` (lista local de nodos con estado de confianza, `getDevicesByUPeerId`) se titulaba «Sesiones y Dispositivos · Dispositivos vinculados a tu identidad», lo que se leía como el registro de la cuenta sin serlo. Ahora vive en la misma sección como **Confianza local entre nodos**, con el texto explícito de que no forma parte del registro firmado. Seguridad queda con PIN, recuperación y zona de peligro.

**El marcador del rail ahora sirve para algo.** `SyncStatusIndicator` era un icono de nube con `cursor: default` que informaba del estado de sincronización sin ofrecer ninguna acción, justo el elemento que uno pulsa cuando quiere gestionar dispositivos. Ahora es clicable y abre Ajustes → Dispositivos, y el tooltip lo dice. Verificado con un clic real sobre la app en marcha: navega a la sección.


### Gestión de dispositivos en móvil: misma sección propia (26/10/2026)

El móvil tenía el problema inverso al de escritorio, agravado: la sección **Seguridad** no contenía nada más que el panel del autorizador. Se renombra a **Dispositivos** (`settings-sections.tsx`, entrada con icono `Smartphone` en Configuración) y se elimina `security-settings-section.tsx`, que era solo un envoltorio del mismo panel.

Los textos dejan de hablar de «vincular dispositivos» dentro de la cuenta y de repetir el rótulo de la sección:

- El panel pasa a titularse **Autorizados en esta cuenta**, con la explicación de qué implica revocar uno.
- La acción de alta es **Añadir otro dispositivo a mi cuenta** y abre la invitación; el diálogo se llama igual y ya no dice «vincula» en ningún sitio.
- El paso de emparejamiento termina en **Terminar vinculación** y la ruta que menciona es **Configuración → Dispositivos**, que es donde está de verdad ahora.

**Escritorio alineado.** Las mismas etiquetas se aplican al acceso y a la sección: «Entrar con la frase de mi cuenta» y «Entrar con un QR de otro dispositivo» sustituyen a «Ya tengo una cuenta» y «Vincular un dispositivo a mi cuenta», el paso de QR deja de titularse «Vincular este dispositivo», y el bloque de alta de la sección pasa a «Añadir otro dispositivo a mi cuenta». Verificado sobre la app en marcha: la sección renderiza con el estilo plano de cabecera y sin la tarjeta redondeada que rompía la convención.

**Verificado en dispositivo**, con un matiz: el dispositivo se quedó sin identidad (`SecureStore.xml` vacío) y Android rechaza la inyección de eventos (`INJECT_EVENTS`), así que no se podía llegar a Configuración pulsando. Se montó la sección en una ruta temporal (`/preview`) y se abrió por deep link (`upeer://preview`), que sí funciona por `adb`. Resultado: cabecera, rótulo en versalitas, icono, texto, aviso de registro no listo y botón de alta se renderizan con el estilo de la sección; la ruta se borró después. De paso salió un fallo real: sin registro, el hook mostraba el error crudo del módulo nativo («JSON Parse error: Unexpected end of input») en una banda roja; ahora los fallos opacos de lectura se callan y habla el aviso de «registro no listo».

**Fallo previo encontrado de paso:** la entrada **Almacenamiento** de Configuración no tenía sección en `renderSettingsSection`, así que abría una pantalla en blanco. Ahora hay `StorageSettingsSection`, que explica que la gestión (uso de disco, descarga, limpieza) aún no está y que los datos se quedan en el almacén local. Verificada en pantalla con la misma ruta temporal.

El truco de la ruta `/preview` + deep link es la forma de revisar cualquier componente de sesión en el móvil sin inyección de eventos: `adb shell am start -a android.intent.action.VIEW -d "upeer://preview"`.


### Enrolamiento en banda por Yggdrasil: dos pasos y ninguno más (26/10/2026)

El flujo fuera de banda pedía tres códigos copiados a mano: la invitación del escritorio, la solicitud del móvil y la respuesta de vuelta al móvil. Para el usuario eso no es vincular, es teletrabajo. La regla de producto es la que se fijó: **generar la invitación en el escritorio, escanear el QR con el móvil y ya está**.

**El canal.** La invitación ya viaja con `authorizerAddress`, la dirección Yggdrasil del dispositivo que autoriza (`EnrollmentOffer.authorizerAddress`, normalizada en `enrollment.ts` y rellenada con `getYggstackAddress()` en `enrollmentAuthorizer.ts`; en Go, `CreateEnrollmentInviteWithAddress`). Con eso el móvil sabe a quién llamar:

- El móvil abre TCP al puerto **50005** del autorizador y manda `{type:'ENROLL_REQ', inviteId, senderAddress, sealed}` (`EnrollmentChannel.requestFrame`).
- El escritorio lo recibe en `routeEarlyPacket`, antes de DHT y SYNC, y lo procesa en `handleInBandEnrollment` (`network/enrollmentChannel.ts`): valida `inviteId` hex de 32 bytes, `nonce`, `ciphertext` y `senderAddress`, emite el certificado y lo registra en el registro firmado.
- La respuesta vuelve por el mismo socket: `{type:'ENROLL_RES', inviteId, ok, sealed}`, y el móvil la instala en `YggdrasilProtocol.handleEnrollmentResponse` → `DeviceEnrollment.installSealed`.
- El hook (`use-device-enrollment.ts`) pasa a fases `idle|sending|waiting|manual` y sondea `getDeviceEnrollmentStatus()` cada 1,5 s durante 120 s. El panel del móvil ya no pide nada: escanear, esperar y listo.

**El paquete no va firmado, y es correcto.** La autenticidad no la da el transporte sino el sobre sellado con el secreto de la invitación (`secretbox`), que solo conocen los dos extremos. Un tercero que vea el paquete no puede descifrarlo ni falsificarlo; el `inviteId` es de un solo uso y caduca.

**El escritorio tampoco pide nada.** `DeviceEnrollmentPanel` genera la invitación, arranca un sondeo de `getAuthorizedDevices()` cada 2 s (máximo 3 minutos) y en cuanto aparece un `deviceId` que no estaba en la foto inicial muestra «Dispositivo añadido a tu cuenta» y avisa a `SectionDispositivos` para que refresque la lista autorizada. Los tres códigos siguen existiendo detrás de «Usar códigos manuales», como escape para cuando los dos nodos no se alcanzan.

**Verificado de punta a punta en dispositivo real**: invitación creada desde el escritorio, deep link con el código en el móvil, y en el log del escritorio aparecen seguidas «Invitación de enrolamiento consumida» y «Enrolamiento en banda completado», con el `deviceId` del móvil dentro de `getAuthorizedDevices()`. Cero copias de códigos.

**Dos trampas que costaron tiempo y quedan apuntadas:**

- **Recompilar el AAR no basta.** `gomobile bind` genera el `.so` dentro del AAR; hay que copiarlo a mano (`jni/arm64-v8a/libgojni.so` → `modules/upeer-transport/android/src/main/jniLibs/arm64-v8a/`) y `classes.jar` a `libs/`. Si no, el APK arranca con el binario viejo y los métodos nuevos responden «método no encontrado». Comprobación barata: `unzip -j app-debug.apk "lib/arm64-v8a/libgojni.so"` y `strings | grep <simbolo nuevo>`.
- **El nodo del móvil tiene que estar levantado** para tener dirección; si el hook no encuentra dirección, `ensureNetworkNode()` arranca el nodo con `startWithPeers`. Y tras `adb shell am force-stop` el dev client vuelve a su pantalla de servidores: hay que relanzar primero `upeer://expo-development-client/?url=...` y después el deep link de la ruta.


### Entrada a la cuenta: dos formas equivalentes (26/10/2026)

La pantalla de acceso ofrecía «Recuperar identidad» con un enlace escondido «No tengo la frase: vincular este dispositivo». Se sustituye por una sola acción, **«Vincular a mi cuenta existente»**, que abre una elección de dos botones iguales:

- **Con la frase de mi cuenta**: se derivan las 12 palabras y la sesión arranca. `DeviceIdentityStore.provision` emite el certificado de dispositivo con la raíz y `AccountRegistryStore.ensure` construye el registro firmado, así que **la vinculación ocurre sola, sin segundo paso ni QR**. No hace falta otro dispositivo encendido, que es la ventaja real de la frase sobre el QR.
- **Con un QR de otro dispositivo**: `DevicePairingPanel` con los tres pasos OOB, para quien no tiene la frase. El resultado es el mismo certificado instalado y `waitForNativeIdentity` en lugar de `setIdentity`.

Las dos rutas terminan en el mismo estado: identidad guardada, certificado de dispositivo instalado y registro firmado. La diferencia es de origen de la clave, no de resultado.

**Limitación abierta y bloqueante para probar el emparejamiento real:** un dispositivo con la raíz **reconstruye el registro con lo que tiene en local**, y lo que tiene en local es solo su propio certificado (`ensure` parte de `listOf(certificateJSON)` cuando no hay estado previo). El registro de la DHT **no lleva los certificados completos**, solo un resumen por dispositivo (`deviceId`, `devicePublicKey`, `authorizedAt`, `expiresAt`, `epoch`), así que no se pueden re-firmar los dispositivos ajenos desde ahí. Consecuencia: si el móvil publica su registro, **el escritorio desaparecería del registro de la cuenta** y perdería la admisión. Para no romper nada mientras se decide cómo se transportan los certificados propios, `publishRegistry` ahora **no publica** cuando la DHT ya anuncia dispositivos que este nodo no conoce (`AccountRegistryDevices.unknownDevices`), y lo deja en el log: «No se publica el registro de cuenta: la DHT ya anuncia N dispositivos que este no conoce». Es una salvaguarda, no la solución: la solución pasa por intercambiar los certificados de dispositivo entre dispositivos propios —el sobre de B10 ya los lleva en los paquetes firmados por dispositivo— y re-firmar el registro con la unión.

### Handshake sin raíz: decisión pendiente (26/10/2026)

Un dispositivo enrolado ya arranca, recibe, sincroniza su ubicación y responde control, pero **no puede abrir una conversación nueva**: `buildHandshakeRequest` (`device_handshake.go:79`) termina en `buildSignedPacket` y `buildHandshakeAccept` llama a `requireSecretKey`, y `SignedPreKeyStore.currentJSON` crea la SPK de cuenta con `createSignedPreKey`. Escritorio está igual: sin `identityState.secretKey`, `sign()` no firma y el envío solo deja «Packet signing key is unavailable».

Hay dos caminos y **no son equivalentes en seguridad**, así que se decide con el usuario antes de implementar:

- **A — Transportar la SPK de cuenta firmada por la raíz durante el enrolamiento.** El dispositivo recibe `spkPub`/`spkSig`/`spkSk` ya firmados por la raíz y los presenta en el handshake. El receptor no cambia nada: sigue validando contra la clave pública de cuenta. **Coste de seguridad:** el dispositivo guarda la clave secreta de la SPK de cuenta, así que quien lo comprometa puede aceptar sesiones X3DH dirigidas a la SPK de cuenta. No obtiene la raíz —no puede firmar como cuenta, ni enrolar, ni revocar—, pero rompe parte del «sin raíz» que la fase quería garantizar. Caducidad y rotación mitigan, no eliminan.
- **B — Handshake firmado por dispositivo y admitido por certificado.** El paquete viaja firmado con la clave de dispositivo y adjunta su certificado; el receptor verifica la firma con `devicePublicKey`, valida el certificado contra la cuenta y comprueba la admisión en el registro (`validateDeviceAdmission`, que ya existe en ambos clientes). Es el modelo correcto y no amplía privilegios, pero exige un `validateControlFrame` que entienda certificados, resolver el registro de cuenta de un contacto **nuevo** (consulta DHT, que ya existe en el móvil) y decidir qué pasa cuando el registro no se puede resolver. También obliga a que escritorio acepte lo mismo, porque el receptor de hoy solo verifica con la clave de cuenta.

Recomendación para la decisión: **B**, con **A** como plan de contingencia si la resolución del registro en el primer contacto resulta demasiado frágil sin conectividad. Mientras no se decida, este residuo de B6 queda explícitamente fuera del camino crítico de B10 (que no lo necesita) y se documenta como puerta de B9.

### Servicio en segundo plano y desconexión desde Ajustes (26/10/2026)

Decisión de producto: **la notificación persistente deja de llevar el botón «Desconectar»**. El control pasa a Ajustes, donde además se decide si el nodo puede seguir vivo con la app cerrada.

Regla: **un solo interruptor, en Ajustes → Conexión, activado por defecto**, que significa «mantener uPeer conectado en segundo plano». Accesible sin buscar en menús ocultos y con el mismo lenguaje que el resto de ajustes.

- **El interruptor manda sobre las tres cosas a la vez:** servicio en primer plano, notificación persistente y supervivencia del proceso al cerrar la app. No son tres ajustes: un nodo que corre sin servicio en primer plano no es fiable, así que se exponen juntos y se explican como uno.
- **Apagado no deja nada vivo.** No se llama a `startForegroundService`, no hay notificación fija y al cerrar la app el proceso `:yggdrasil` **no puede quedar en marcha**. Eso obliga a tocar más de un sitio: `UPeerTransportModule.start`/`startWithPeers` (líneas 47 y 55) son los que arrancan el servicio desde JS, pero el manifest declara `android:stopWithTask="false"` en un proceso aparte `:yggdrasil`, `onTaskRemoved` vuelve a publicar el primer plano si hay bridge, y `onStartCommand` con `intent == null` llama a `restoreNode()`, que reconstruye el nodo desde `configStore` cuando el sistema reinicia el servicio. Con el interruptor apagado hay que: no promover a primer plano, parar el nodo al perder el foco de la app (`AppState` → `background`, o `onTaskRemoved`/`onDestroy`), devolver `START_NOT_STICKY` y limpiar `configStore` para que `restoreIfNeeded` no encuentre nada que restaurar.
- **Consecuencia honesta que la UI debe decir:** con el interruptor apagado no llegan mensajes con la app cerrada, no hay notificaciones y los envíos en vuelo esperan a que se abra. El texto del interruptor lo dice («Sin esto, uPeer solo recibe mensajes mientras la app está abierta»), y el estado vacío de Conexión deja de prometer «conectado» cuando el nodo está parado a propósito.
- **Desconectar sale de la notificación.** `UPeerNotifications.foregroundNotification` construye el `PendingIntent` de `ACTION_STOP` en la acción «Desconectar» (líneas 50-59). Se retira esa acción; la notificación sigue siendo el aviso de que el nodo está activo, pero sin control destructivo. La desconexión manual pasa a Ajustes → Conexión, junto al interruptor, con el mismo `stop()` del servicio (que ya hace `stopForeground(STOP_FOREGROUND_REMOVE)` y `stopSelf()`).
- **La justificación del tipo de servicio se queda corta.** El manifest declara `foregroundServiceType="specialUse"` con el subtipo «…requires an ongoing visible connection notification and user disconnect control». Al mover el control, ese texto tiene que pasar a describir el interruptor de Ajustes, o la declaración deja de ser cierta.
- **El interruptor se persiste como preferencia de UI, no como estado del nodo.** Va en `Preferences` (`src/domain/preferences.ts`, hoy sin campo de conexión) con migración: ausente = activado, y quien tenga el JSON viejo conserva el comportamiento actual. El servicio no lo lee de disco; lo recibe por el módulo, igual que `setNotificationPrefs` y `setReadReceipts`.
- **Riesgo de arranque en Android 8+:** con el interruptor apagado no se puede arrancar un servicio en primer plano desde segundo plano sin una excepción válida, así que si el usuario apaga y luego el sistema intenta restaurar, la app debe fallar en silencio y dejar el nodo parado. Con el interruptor encendido el arranque sigue igual que hoy (`startForegroundService` + `ensureForeground`).

Queda pendiente:

1. Añadir el campo a `Preferences` y su migración (ausente = activado), con prueba unitaria del valor por defecto.
2. Quitar la acción «Desconectar» de `foregroundNotification` y trasladar el botón a la sección de Conexión de Ajustes, junto al interruptor.
3. Implementar el camino apagado: sin primer plano, parada al pasar a segundo plano y `START_NOT_STICKY` con `configStore` limpio.
4. Verificar en dispositivo con la app cerrada en las dos posiciones del interruptor: con él encendido el nodo sigue recibiendo y notificando; con él apagado el proceso `:yggdrasil` no aparece en `adb shell ps -A`.

### El `.so` de Go tiene dos copias y solo una entra en la APK (26/10/2026)

La app arrancaba y moría con `FATAL EXCEPTION` → `UnsatisfiedLinkError: No implementation found for boolean app.upeer.transportbridge.transportbridge.CryptoIdentity.isDeviceOnly()` al arrancar el nodo en modo dispositivo. **No era el dispositivo ni el transporte de `adb install`**: el binario nativo estaba desincronizado con las bindings Java.

Lo que hay que saber para no repetirlo:

- **El AAR no se ensambla.** `modules/upeer-transport/android/libs/upeer-transport.aar` contiene su propio `jni/arm64-v8a/libgojni.so`, pero el build no lo consume: `build.gradle` añade `android.sourceSets.main.jniLibs.srcDirs += "$projectDir/src/main/jniLibs"`, y **esa es la única fuente del `.so` que llega al APK**. Comprobado quitando esa línea: `assembleDebug` produce un APK sin `libgojni.so`. Es decir, regenerar el AAR con `gomobile bind` **no** actualiza lo que se instala.
- **Regenerar el AAR no basta: hay que copiar su `.so`.** El del 25/10 tenía 19 594 528 bytes y el de `src/main/jniLibs` era del 24/10 con 19 568 016; `unzip` del AAR y copia sobre `src/main/jniLibs/arm64-v8a/libgojni.so` es el paso que faltaba. Los dos `.so` están versionados, así que el desfase es invisible en `git status` salvo por el tamaño.
- **Cómo se detecta sin instalar:** `strings libgojni.so | grep -c Java_app_upeer_transportbridge_transportbridge_CryptoIdentity_isDeviceOnly`. El viejo daba 0 y el bueno ≥ 1. Merece la pena repetirlo tras cualquier `gomobile bind` que añada símbolos nuevos a `CryptoIdentity`.
- **La comprobación barata antes de instalar:** descomprimir el APK (`unzip -j app-debug.apk "lib/arm64-v8a/libgojni.so"`) y contar el símbolo ahí, no en el AAR ni en `jniLibs`. Es el binario que va a correr.

Y un detalle de instalación que costó más de lo que debería: **`adb install` con streaming falla en MIUI** (`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`) aunque el dispositivo esté desbloqueado y con la depuración permitida. La forma fiable es `adb install -r -t --no-streaming <apk>`. Para lanzar la app sirve `adb shell monkey -p com.upeer.upeer -c android.intent.category.LAUNCHER 1`, porque `adb shell input keyevent` falla con `SecurityException: INJECT_EVENTS` en esta ROM y `settings put` con `WRITE_SECURE_SETTINGS`.

### Transporte local sin internet: paridad con Briar (24/10/2026)

Decisión: el Bluetooth de Briar entra en el roadmap como **B11**, no como una función de una fase de producto, porque no es una pestaña: es un transporte más y obliga a cambiar cómo se firman y se admiten los paquetes.

Hallazgos que condicionan el diseño:

- **El paquete firmado está atado a una dirección IPv6.** `buildSignedPacketObject` exige `senderYggAddress` y `VerifySignedPacket` la valida como dirección de Yggdrasil. Un `CHAT` por RFCOMM no se puede firmar hoy sin inventar una dirección, y relajar esa comprobación afectaría a todos los paquetes y a la admisión de B7.
- **El rate limit es por IP.** `InboundRateLimiter` indexa `ip:<addr>`; sin IP no hay cubo, y la admisión consulta `LocationBlock`, que tampoco existe en un enlace local.
- **La red del bridge es IP-céntrica.** `dialTCP`, `listenTCP`, `acceptTCP`, `connectionAddress` y `readFrame`/`writeFrame` son métodos del `Bridge`, que además aporta `address()` para firmar. RFCOMM no tiene equivalente, así que hace falta un `TransportLink` y dejar el `Bridge` solo para criptografía.
- **El framing ya vale.** Los frames llevan prefijo de longitud de 4 bytes y el lector de `YggdrasilInbound` es agnóstico del socket, así que el flujo de bytes de RFCOMM se puede reutilizar sin tocar el códec.
- **Lo que Briar aporta y no es obvio:** descubrimiento con tope de 10 s y cancelación explícita, límite de conexiones por enlace (`BluetoothConnectionLimiter`), identidad intercambiada solo tras el handshake cifrado —el nombre de dispositivo no lleva `upeerId`— y el supuesto de que un adversario **sí** puede observar el canal de corto alcance de forma limitada. Ese supuesto implica relleno de longitud constante, que hoy no existe.
- **El coste real es la MAC.** Es un identificador estable visible para cualquiera que escanee, así que la privacidad del transporte local es peor que la de Yggdrasil por diseño; hay que documentarlo en la UI y no prometer anonimato en modo local.

Reglas de estilo acordadas para toda pantalla nueva:

- Solo tokens de `src/constants/theme.ts`; ningún color literal.
- **Nunca `lineHeight` en texto.** Ni `Text`, ni `Paragraph`, ni `SizableText`: el interlineado lo pone la fuente. Se ha repetido la corrección demasiadas veces (última, el estado vacío de Chats); el aviso vive también en `AGENTS.md`. Única excepción, el glifo de inicial de `ProfileAvatar`.
- El ripple sale de `theme.ripple`. `NativeRippleButton` recorta con `overflow: 'hidden'` por defecto y todo contenedor pulsable con esquinas redondeadas debe recortarlo también, o el ripple se sale de la forma.
- Los estados vacíos repiten el patrón de «Aún no hay llamadas»: círculo de 64 px con `theme.backgroundElement`, icono de 28 px en `theme.placeholder`, título 17/600 y `Paragraph` centrado; si hace falta una acción, se añade el botón estándar debajo. Nada de bordes discontinuos ni tamaños propios.
- Las pantallas de primer nivel usan `theme.background`; el círculo del estado vacío necesita ese contraste para verse.
- Las cabeceras de lista repiten el patrón de Chats: `H1` 24/700 y, a la derecha, acciones de icono de 44×44 (`headerButton`); la búsqueda es la misma caja sin borde de 35 px de alto y radio 8, con la lupa dentro y el aspa de limpiar condicional, calcada de `SidebarSearch`.
- Las fichas de contacto y de grupo son pantallas completas empujadas al stack (`/contact/[upeerId]`), no hojas inferiores: cabecera de 44×44 con `ArrowLeft` y título 17/700, hero centrado con avatar 96, fila de tres acciones circulares de 52, campos copiables y listas de filas de 58 con icono 22. Las acciones que aún no existen se muestran atenuadas (`opacity` 0.45) con la etiqueta «Pronto», siguiendo la distribución de `ContactInfoPanelMainView` de escritorio, en vez de ocultarlas.
- Las cuatro pestañas principales comparten el mismo bloque de cabecera y el mismo relleno de contenido (`paddingTop` 20, `paddingHorizontal` 20, `gap` 12); en Llamadas eso queda como `H1` 24/700 sin acción hasta que exista «nueva llamada». Cuando llegue el historial, sus filas reutilizan el item de conversación.
- Las filas de contacto reutilizan el item de conversación: `ContactTrustAvatar` de 55 px, título 15/600 con el dato secundario a la derecha (hora en chats, estado en contactos), segunda línea de 13 con `numberOfLines={1}`, `minHeight` 80 y separador `hairline` a sangre (`marginHorizontal: -12`). No se inventan tarjetas con borde y fondo propio para listas.
- El relleno horizontal va en el contenido de cada pantalla, nunca en el `Tabs`.
- Las cabeceras de sección repiten el patrón icono 16 px + título 14/600 + `Info` + subtítulo 12 px con borde inferior.
- Los formularios siguen el patrón de escritorio: texto explicativo, campos con etiqueta y ayuda, acción principal deshabilitada hasta que el formulario es válido.

### Paridad visual: chips y campos de búsqueda (24/10/2026)

El usuario detectó que los chips de filtrado de Chats y los campos de búsqueda del móvil se habían inventado. **Escritorio es la referencia y no se reinterpreta.**

- **Chips.** Salen de `SidebarSearch` (`Chip` de Joy UI): `Todos`, `No leídos`, `Favoritos` y `Grupos`. Activo `variant="solid" color="primary"` con `fontWeight` 600; inactivo `variant="soft" color="neutral"` con 400; radio de píldora completa, `size="sm"` y separación de 8 en una fila con scroll horizontal sin indicador. No hay chip «Archivados»: en Desktop archivar es una acción por conversación (`ContactItemActions`), no una pestaña.
- **Campos de búsqueda sin borde.** Ningún campo de búsqueda lleva borde permanente: caja `theme.backgroundElement`, radio 8, lupa dentro, `Input` con `borderWidth={0}` y aspa de limpiar a la derecha cuando hay consulta. Se enciende el borde solo al enfocar, como el `:focus-within` de `SidebarSearch`.
- **Una sola altura, la de Chats.** La referencia es la barra de búsqueda de Chats tal como quedó en el móvil: **caja de 50, icono de 18, texto de 14 y aspa de 16**. Contactos y el selector de contactos estaban en 35 y con un `Input` de 40 que desbordaba la caja; se igualan a la de Chats. `SearchField` es la única fuente de verdad para que ninguna pantalla nueva vuelva a inventar la medida.
- **Placeholder.** «Buscar un chat o iniciar uno nuevo» en Chats, igual que Desktop, en lugar de un texto propio.
- **Estado vacío por filtro.** Cada filtro tiene su mensaje: `Grupos` dice «Sin grupos», `Favoritos` «Sin favoritos» y `No leídos` «Sin mensajes no leídos», como los `EmptyState` de `SidebarListPanel`.
- **Iconos.** El móvil dibuja con `@tamagui/lucide-icons-2`; escritorio sigue con `@mui/icons-material` en 94 archivos y 145 iconos distintos. La migración de escritorio a Lucide queda como deuda para que un mismo concepto tenga el mismo trazo en ambos clientes.

### Contador de no leídos en la pestaña Chats (24/10/2026)

Escritorio pone un `Badge` sobre el icono de Chats en su rail de navegación. El móvil no tenía nada.

- **La regla es la de escritorio.** Cuenta conversaciones, no mensajes: las que tienen mensajes recibidos sin leer y cuyo último mensaje no es propio. `countUnreadChats` es una función pura con cinco pruebas.
- **El límite también.** Por encima de 99 se escribe `99+`, como el `badgeContent={unreadChatCount > 99 ? '99+' : unreadChatCount}` de `NavigationRail`.
- **El color es el del contador de la lista, no el de escritorio.** La primera versión usó `theme.danger` y el usuario lo rechazó: en oscuro es un rojo salmón y el número blanco encima quedaba sucio. El móvil ya tiene su propio color para no leídos —`theme.accent` con `theme.onAccent`— en la fila de cada conversación, y la pestaña usa el mismo para que no haya dos rojos distintos en la misma pantalla.
- **El número sale centrado.** El `tabBarBadge` de React Navigation impone su propio `lineHeight` y su propio tamaño de fuente, y el dígito quedaba descentrado dentro de la píldora. `TabBadge` se dibuja dentro del `tabBarIcon` con `View` + `SizableText`, `includeFontPadding: false` y `textAlignVertical: center`, de modo que la píldora (18 de alto, radio 9, mínimo 18 de ancho) y el texto comparten centro.
- **Se refresca solo.** `useUnreadChats` consulta cada 3 s, así que abrir la conversación limpia el contador sin recargar la app.
- **Forma definitiva del badge (decisión del usuario).** Un poco más grande —20 de alto, radio 10, mínimo 20 de ancho, texto de 11— y **centrado en altura respecto al icono**, no pegado a su esquina. Cuando aparece, el icono **se desplaza 4 px a la izquierda** y el badge se superpone **ligeramente hacia la derecha**, con **animación** en ambos: `translateX` del icono y `opacity`/`scale` de la píldora, las dos por el driver nativo. Al desaparecer se revierte con la misma animación. Nada de `tabBarBadge` de React Navigation: el badge se dibuja dentro de `tabBarIcon`.

### Reputación: criterio técnico, no etiqueta social (24/10/2026)

- **No se muestra.** Desaparece de la ficha del contacto, de la tarjeta de confianza y de la pantalla de solicitud. `ContactTrustAvatar` deja de pintar insignia; el dato sigue existiendo en el servicio.
- **Única excepción.** Una reputación muy mala se avisa como señal de seguridad, porque el usuario necesita saber que el nodo está aislado del grafo. El umbral se define una vez y se comparte entre clientes.
- **Uso interno intacto.** PoW, renovaciones por confianza, elección de relay de llamadas, admisión de vault y rate limiting por identidad siguen leyendo el score.
- **Rediseño pendiente: por usuario, no por nodo.** `computeScore` puntúa una identidad, pero lo que mide de facto es el nodo que escribe. Un usuario con móvil y escritorio obtiene dos puntuaciones distintas de la misma persona. Hay que agregar por cuenta con los dispositivos verificados como unidad —decidir peso por dispositivo, función de agregación y qué pasa si un dispositivo comprometido intenta arrastrar la cuenta— y cubrirlo con pruebas de que añadir dispositivos no mejora ni empeora la cuenta por sí solo.

### Perfil en el handshake y propagación (24/10/2026)

El usuario reportó que en escritorio el móvil aparecía como «Peer d679» sin avatar, y que el móvil no pintaba el avatar del escritorio. Eran dos fallos distintos:

- **El móvil nunca mandaba su perfil.** `OutboxPacketFactory.handshakeRequest` pasaba alias `""` y no existía avatar. Ahora el puente expone `setProfile(alias, avatar)`, el perfil se persiste en el estado (`profile`) y viaja en `HANDSHAKE_REQ` y `HANDSHAKE_ACCEPT` con `BuildHandshakeRequestWithProfile`/`BuildHandshakeAcceptWithProfile`. El límite de avatar del puente se alinea con el de escritorio (2 MB de data URI); antes eran 96 KiB y habría rechazado fotos válidas.
- **El perfil no se propagaba al cambiar.** `setProfile` compara el perfil nuevo con el anterior y, si cambia, olvida a quién ya lo anunció. El anuncio real va por peer y **se dispara con el primer paquete verificado que llega de ese contacto**, no al arrancar: `announceProfileTo(upeerId)` manda un `HANDSHAKE_REQ` con el perfil en un hilo propio. La primera versión hacía un barrido al arrancar y quedaba colgada en `dialTCP` contra peers muertos, bloqueando la llamada nativa del hilo de JS; se descartó.
- **El móvil recibía el avatar del escritorio pero lo tiraba.** `YggdrasilProtocol` copiaba `alias` de los metadatos del handshake y no `avatar`, y `rememberPeer` no lo guardaba. Ahora ambos campos se copian y `ProtocolConversations` los proyecta; Chats, cabecera, ficha y lista de Contactos los leen.
- **Verificado en vivo:** tras el cambio, escritorio muestra el móvil como «dev» con su foto (62 KB) y el móvil pinta la foto del escritorio en Chats.
- **Falsa alarma descartada:** el `RangeError: Too few parameter values were provided` al editar un mensaje recién enviado no era un fallo del producto. El driver de pruebas pasaba un id equivocado (`sent.id` es el identificador del mensaje; usar otro campo rompía la consulta). Con el id correcto, `sendChatUpdate` funciona justo después de `sendMessage`.

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
- [x] Diseñar lista de conversaciones, búsqueda, chat, perfiles, seguridad y ajustes.
- [x] Rediseñar Contactos y el alta de conversación con la referencia de escritorio: cabecera de sección con icono, recuento, búsqueda local, lista de tarjetas, estado vacío con acción y alta en hoja inferior (`ContactFormSheet`) con identidad, alias y `Escanear QR del contacto`.
- [x] Sustituir el botón flotante de Chats por la acción de cabecera y la hoja `NewConversationList` (nueva conversación, nuevo grupo y contactos filtrables).
- [ ] Adaptar gestos, teclado, safe areas, orientación, tamaños de texto y lector de pantalla.
- [x] Implementar temas y tokens visuales compartibles; no portar widgets Joy UI/MUI a React Native.
- [x] Unificar el chrome del sistema con el tema: `ThemeChrome` aplica `SystemUI.setBackgroundColorAsync` y `NavigationBar.setStyle` en tiempo de ejecución (el cambio de `uiMode` no recrea la Activity) con el plugin `expo-navigation-bar` y `enforceContrast: false`.
- [x] Tokenizar el ripple: `theme.ripple` (`#0000001F` claro, `#FFFFFF30` oscuro) consumido por `NativeRipplePressable` y `NativeRippleButton`; un color fijo es invisible en superficie clara.
- [x] Regla de padding: el `Tabs` no aplica relleno horizontal (`sceneStyle` solo pinta fondo) y cada pantalla lo pone en su propio contenido. Un relleno en el navegador deja ver el gris de la escena tras las pantallas que pintan `theme.background`.
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

- [x] Añadir contactos por upeer ID o QR: el alta exige `uPeerID@dirección` (64 hex y prefijo `200:`/`300:`), rechaza el alta propia, registra el peer en el servicio nativo y permite escanear el QR de la tarjeta de contacto del escritorio (`ContactFormSheet` + `QrScannerSheet`); el alias es opcional y, si falta, se derivan los 12 primeros hex.
- [ ] Generar en el móvil la tarjeta de contacto en QR (hoy solo se muestra la identidad en texto y QR propio en Perfil) y aceptar solicitudes entrantes con reputación y bloqueo.
- [x] Portar chat E2EE y ratchet respetando los vectores de interoperabilidad: `CHAT` con X3DH/Double Ratchet, `ACK`, `CHAT_UPDATE` y `DR_RESET` funcionan contra Desktop.
- [x] Portar edición (`CHAT_UPDATE` con `ChatEditPolicy` y `version`).
- [ ] Portar respuestas (`replyTo` ya viaja en el paquete pero la UI no lo ofrece), reacciones (`CHAT_REACTION`), borrado (`CHAT_DELETE`), reenvío, selector de emoji y paginación real del historial (hoy se piden 200 mensajes sin cursor).
- [x] Estados de envío, entrega y lectura: el móvil emite `READ` al abrir la conversación y aplica el `READ` entrante como `read` en los mensajes propios, así que la UI pinta `delivered` y `read` reales. `synced`/`vaulted` siguen fuera hasta que exista la sincronización.
- [ ] Implementar outbox durable para envío cuando se recupere conectividad.
- [ ] Portar presencia y escritura (`TYPING` y heartbeat de protocolo, hoy ausentes) con intervalos respetuosos con la batería.
- [x] Recibir y enviar tarjetas de contacto: se envían como contenido de un `CHAT` normal y se pintan como tarjeta, con «Añadir a contactos» si no están en la agenda. No hay paquete `CHAT_CONTACT` porque escritorio no lo emite: `sendContactCard` manda un `CHAT` con el JSON de la tarjeta y su renderer lo parsea.
- [ ] Preferencias de notificación (mensajes, solicitudes, sonido) y agrupación entre conversaciones; el enlace profundo al chat y la previsualización privada ya funcionan.
- [x] Implementar notificaciones locales y acciones compatibles con plataforma: canales «Conexión» y «Mensajes», `notifyMessage` por conversación y acción «Marcar leído».
- [ ] Permitir ocultar previsualizaciones y controlar privacidad en pantalla bloqueada.
- [ ] Sincronizar paquetes recibidos por servicio nativo con base de datos y UI al reanudarse.
- [ ] Probar ACK perdido, mensajes repetidos, offline prolongado, cambios de clave y reintentos.

#### Criterios de salida

- [ ] Mensajes directos E2EE pasan pruebas móviles↔desktop.
- [ ] Reiniciar app no pierde mensajes pendientes ni estado ratchet.
- [ ] Las notificaciones respetan preferencias de privacidad.
- [ ] La presencia no requiere actividad innecesaria en segundo plano.

### Fase 10 — Vault, offline y sincronización entre dispositivos

> Estado (23/10/2026): el móvil no implementa ningún `VAULT_*` ni ningún `SYNC_*`. Esta fase sigue entera.

#### Objetivo

Portar recuperación offline y continuidad multidispositivo sobre B6 y B10. No se reimplementa el self-sync actual de Desktop con mnemonic/SPK compartidas.

#### Tareas

- [ ] Portar vault, renovación de tokens y punteros DHT.
- [ ] Portar recuperación de mensajes y eventos de edición, borrado, reacciones y lectura.
- [ ] Portar recuperación segmentada de adjuntos y su deduplicación.
- [ ] Añadir límites configurables de datos, almacenamiento, batería y replicación.
- [x] Portar la UI de dispositivos propios, emparejamiento, confianza y revocación sobre el registro firmado de B6; la confianza local no autoriza. El servicio ya purga el estado del dispositivo revocado y descarta sus paquetes; falta sincronizar el registro nuevo con los demás dispositivos.
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

> Estado (23/10/2026): el móvil no implementa `GROUP_MSG`, `GROUP_ACK`, `GROUP_INVITE` ni `GROUP_UPDATE`. Esta fase sigue entera.

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

> Estado (23/10/2026): el móvil no implementa ningún `FILE_*`. El clip del composer y el micrófono avisan de que la función está pendiente.

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

> Estado (23/10/2026): el móvil no implementa ningún `CALL_*` ni tiene captura, codecs o pipeline de medios. La pestaña Llamadas es un estado vacío y la cabecera del chat ofrece iconos que avisan «pendiente».

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