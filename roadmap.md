# Roadmap móvil uPeer con Expo

_Estado: port en curso a 22 de octubre de 2026. Identidad, perfil local, nodo Yggdrasil, transporte saliente/entrante TCP y chat bidireccional con un contacto Desktop ya existente están verificados; B0 y B1 están cerradas y B2 es el siguiente bloqueo de DHT/bootstrap._

> Documento único de alcance y orden. Las fases **B0–B8** son la secuencia obligatoria de red y descubrimiento; las **fases de producto 4–10** solo empiezan después de B8. Las secciones «Fase 0» a «Fase 15» del anexo conservan requisitos detallados, pero **no son el orden de ejecución**.

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
| Protocolo uPeer | Parcial | `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT` firmados con SPK nativa, X3DH, `SEALED`/`CHAT`, ACK, compresión y outbox validados contra un peer Desktop simulado en ambos sentidos; `DR_RESET`, ACK perdido, duplicados, rate limiting por origen/identidad y política de replay por UUID y propiedad del mensaje probados. Las SPK solo avanzan en handshakes; un `DR_RESET` con el mismo `spkId` se acepta como reset idempotente mediante `resetId`, y una SPK obsoleta no reinicia la sesión. El chat cruzado se verificó con un contacto ya conocido: el handshake móvil aún no envía `powProof`, obligatorio en Desktop para contactos nuevos, ni `addresses`/`ephemeralPublicKey`. Hay prototipos locales de firma y LocationBlock en Go, sin binding ni vector Desktop, que no cuentan como DHT. Faltan alta de contacto nuevo desde móvil, DHT, bootstrap, cambio de identidad del contacto y offline prolongado. |
| Persistencia | Parcial | Estado de protocolo cifrado en Keystore con peers, sesiones de ratchet, últimos 500 mensajes y outbox; sin DB relacional ni grupos. |
| UI | Parcial | Onboarding, perfil, contactos, conexión, mapa, lista de conversaciones y chat conectados al protocolo nativo; estados pendiente/enviado/entregado, no leídos, ACK y notificaciones locales visibles. Faltan adjuntos, búsqueda y llamadas. |

**Último hito cerrado:** P1 (primer frame entrante Desktop→móvil) y P2 (protocolo headless persistente). El detalle está en [Hitos y prioridad vigente](#hitos-y-prioridad-vigente). El siguiente bloqueo es DHT; se ejecuta como B0–B8.

## Base actual de escritorio

La referencia funcional está en el repositorio desktop asociado y sus módulos de protocolo, red e identidad.

El proyecto desktop asociado incluye identidad BIP39 y upeer ID; firmas Ed25519; cifrado E2EE X25519/Salsa20/Poly1305 y ratchet; huellas y alertas de cambio de clave; transporte sobre Yggdrasil; DHT Kademlia; bootstrap y roaming firmado; contactos; presencia; chat con ACK/lectura, respuestas, reacciones, edición y borrado; vault y recuperación offline; grupos, roles y sincronización multidispositivo; transferencia cifrada de archivos; notas de voz; configuración y diagnósticos; y llamadas cuya implementación tiene partes completas y otras aún pendientes.

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

## Estado de las fases bloqueantes B0–B8 (22 de octubre de 2026)

- **B0 — completada.** La derivación BIP39/Ed25519/upeer ID tiene vector contra Desktop. Canonicalización, firma de `HANDSHAKE_REQ`/`ACCEPT`, framing, sealed boxes, X3DH, Double Ratchet, ACK y compresión tienen vectores o pruebas cruzadas. Los 12 tipos DHT, LocationBlock multi-dirección/legacy y `DHT_UPDATE` tienen fixture generado por Desktop y pruebas en Go, JS y Kotlin; el validador de Desktop acepta el corpus.
- **B1 — parcial local, no integrado.** Existe un prototipo Go de firma canónica y `BuildSignedPacket` validado con pruebas de ida y vuelta y contra los vectores de B0, pero vive en la raíz del bridge y no está expuesto al binding gomobile ni a un códec Kotlin.
- **B2 — parcial local, no integrado.** Existe un prototipo Go de `BuildLocationBlock`/`VerifyLocationBlock` con normalización de direcciones, TTL, `deviceId`, `deviceMeta` y compatibilidad legacy, ya cubierto por vectores de Desktop. No está expuesto a Kotlin; faltan secuencia DHT persistente, `deviceId` estable y monotonía.
- **B3–B6 — no iniciadas.** Códec DHT, Kademlia, bootstrap/descubrimiento, `DHT_UPDATE` y roaming no están portados. Los archivos Kotlin adelantados sin integrar se retiraron para no confundir prototipo con implementación.
- **B7 — parcial.** El servicio `:yggdrasil` ya es dueño del nodo, listener, protocolo, ratchets, outbox y persistencia cifrada; falta integrar el trabajador DHT y su estado observable.
- **B8 — no iniciada.** No hay validación DHT Desktop↔móvil, fuzzing de paquetes ni presupuesto de batería/red para descubrimiento.
- **Fases de producto 4–10 — no iniciadas como paridad.** El chat directo con un contacto ya conocido funciona en ambos sentidos y está expuesto en la UI, pero faltan grupos, archivos, llamadas, vault y notificaciones avanzadas.

### Disposición del trabajo local no integrado

| Archivo | Estado | Acción antes de continuar |
|---|---|---|
| `transport-bridge/dht_signing.go` | Prototipo Go, pruebas en verde | B1: mover la lógica a `transport-bridge/dht/` y dejar un adaptador en la fachada gomobile |
| `transport-bridge/location_block.go` | Prototipo Go, pruebas en verde | B2: mover a `transport-bridge/dht/` y exponerlo por el bridge |
| `transport-bridge/dht_signing_test.go` | Prueba del prototipo | Mover junto al paquete `dht` cuando se haga B1 |
| `DhtTypes.kt`, `DhtRoutingTable.kt`, `DhtValueStore.kt` | Retirados; no integrados | Rehacerlos en B4 bajo `app/upeer/transport/dht/kademlia/`, no en la raíz |

Ningún prototipo cuenta como fase completada hasta que tenga vector Desktop, binding y prueba cruzada.

## Orden por fases bloqueantes

Cada fase B solo puede empezar cuando la anterior tenga su criterio de salida demostrado. Las fases de producto (4–10) no se adelantan para tapar huecos de red: dependen de B8.

| Fase | Bloquea a | Entrega mínima | Estado |
|---|---|---|---|
| B0 | B1–B8 | Vectores canónicos y harness JS/Go/Kotlin | Hecha |
| B1 | B2–B8 | Firma/verificación de paquetes DHT en el bridge y en Kotlin | Hecha |
| B2 | B4–B8 | LocationBlock firmado, `dhtSeq`, `deviceId` y TTL | Parcial local |
| B3 | B4–B8 | Códec DHT estricto, límites y admisión | No iniciada |
| B4 | B5–B8 | Kademlia: routing, store, protocolo y queries pendientes | No iniciada |
| B5 | B6–B8 | Bootstrap y descubrimiento diversificado | No iniciada |
| B6 | B7–B8 | Location blocks en DHT, `DHT_UPDATE`, renovación y roaming | No iniciada |
| B7 | B8 | Trabajador DHT dentro de `:yggdrasil` y estado observable | Parcial |
| B8 | Producto | Interoperabilidad Desktop↔móvil, fuzzing y presupuesto | No iniciada |

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
    DhtPacketCodec.kt
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

**Estado (22/10/2026): completada.** El fixture `dht_vectors.json` cubre canonicalización, firma, 12 tipos DHT, LocationBlock multi-dirección con `deviceId`/`deviceMeta`, bloque legacy y `DHT_UPDATE`. Go, JS y Kotlin reproducen los mismos bytes y firmas; el validador de Desktop acepta el corpus. La prueba de campo con dos instalaciones queda en B8.

### B1 — Paquetes firmados canónicos en bridge y binding

**Depende de:** B0.

- [x] Prototipo Go `BuildSignedPacket`/`VerifyCanonicalJSON` con rechazo de campos reservados y JSON trailing.
- [x] Mover la lógica a `transport-bridge/dht/` y dejar en la raíz solo el adaptador gomobile.
- [x] Exportar `buildSignedPacket`, `verifyCanonicalJSON` y `verifySignedPacket` en el bridge; regenerar el binding Java.
- [x] Exponer en Kotlin un códec de paquete firmado que no permita inyectar `signature`, `senderUpeerId` ni `senderYggAddress`.
- [x] Probar en JVM: firma válida, firma alterada, clave distinta, `upeerId` que no deriva de la clave, campos reservados, JSON no objeto y datos trailing.

**Salida:** Kotlin firma un `DHT_PING` y Desktop lo acepta; Desktop firma el mismo paquete y Kotlin lo acepta.

**Estado (22/10/2026): completada.** `transport-bridge/dht/` contiene canonicalización y paquetes firmados; la raíz conserva el adaptador gomobile. El binding regenerado expone `buildSignedPacket`, `verifyCanonicalJSON` y `verifySignedPacket`, y `DhtPacketCodec` aplica la política en Kotlin con pruebas JVM de firma válida, alteración, clave distinta, `upeerId` incoherente, campos reservados, dirección inválida, JSON no objeto y trailing. La firma JDK ya reproduce los bytes de Desktop en `DhtCanonicalVectorTest`; la prueba con dos instalaciones queda en B8.

### B2 — LocationBlock, secuencia y dispositivo

**Depende de:** B1.

- [x] Prototipo Go de construcción y verificación, TTL 30/60 días, normalización, deduplicación y orden de direcciones, `deviceId` máximo 128 y compatibilidad legacy.
- [ ] Exportar construcción/verificación al binding y añadir el códec Kotlin equivalente.
- [ ] Persistir `dhtSeq` monotónico por identidad; incrementarlo una sola vez por cambio real de dirección y conservarlo tras reinicio.
- [ ] Derivar un `deviceId` estable por instalación desde la clave de dispositivo, nunca desde la UI.
- [ ] Verificar el bloque derivando el `upeerId` de la clave pública; no confiar en el campo del bloque.
- [ ] Probar bloque expirado, TTL fuera de rango, direcciones inválidas/duplicadas, secuencia menor, salto de secuencia abusivo, `deviceId` largo y bloque legacy.

**Salida:** un cambio de dirección genera un bloque verificable por Desktop, con secuencia creciente y sin aceptar un bloque antiguo o de otra clave.

### B3 — Códec DHT y política de admisión

**Depende de:** B1.

- [ ] Portar los tipos exactos de `kademlia/protocol.ts` y los mensajes legacy de `dhtExchange.ts`/`legacyHandlers.ts`.
- [ ] Fijar límites de nodos por respuesta, tamaño de valor, longitud de claves, `queryId`, `nodeId`, direcciones y JSON total.
- [ ] Aplicar rate limiting DHT por origen y tipo con los mismos umbrales que Desktop antes de asignar memoria o despachar.
- [ ] Rechazar tipos desconocidos, respuestas sin query pendiente y paquetes con `senderUpeerId` incoherente.
- [ ] Añadir pruebas de paquete vacío, gigante, anidado, truncado y con campos de más.

**Salida:** un corpus inválido no provoca asignaciones desmedidas ni respuestas de amplificación; cada paquete válido se decodifica igual que en Desktop.

### B4 — Núcleo Kademlia

**Depende de:** B2, B3.

- [ ] Portar IDs de 160 bits (SHA-256 truncado a 20 bytes), XOR, 160 buckets, K=20 y α=3.
- [ ] Implementar routing table con evicción del contacto más antiguo, `lastSeen`, búsqueda de los K más cercanos y refresco de buckets.
- [ ] Implementar value store con límite de 10 000 entradas, TTL de 30 días, deduplicación y valores multidispositivo ordenados por `dhtSeq`.
- [ ] Implementar queries pendientes con timeout, limpieza y respuesta solo para la query correspondiente.
- [ ] Portar los handlers `PING`, `PONG`, `FIND_NODE`, `FOUND_NODES`, `FIND_VALUE`, `FOUND_VALUE`, `STORE` y `STORE_ACK`.
- [ ] Probar una red simulada de tres nodos: descubrimiento, almacenamiento, búsqueda, expiración y contacto que no responde.

**Salida:** dos instancias Kotlin y una Go intercambian `FIND_NODE`/`STORE` con los mismos resultados que Desktop y sin bloquearse por un peer caído.

### B5 — Bootstrap y descubrimiento

**Depende de:** B4.

- [ ] Cargar seeds desde contactos existentes, archivo local y DNS TXT `dht-seeds.upeer.chat`; validar `upeerId`, dirección y clave antes de añadirlos.
- [ ] Implementar bootstrap con mínimo de 10 nodos y reintento cada 30 s, con deduplicación y backoff.
- [ ] Añadir descubrimiento LAN `ff02::1` solo si Android lo permite en el servicio; documentar restricciones de multicast y batería.
- [ ] Persistir únicamente contactos verificados y acotar su número.
- [ ] Probar arranque sin seeds, seeds inválidas, seed Desktop real, reinicio y cambio de red.

**Salida:** el móvil pasa de cero a una tabla con al menos 10 nodos verificados usando un seed Desktop, y se recupera tras reiniciar el servicio.

### B6 — Location blocks en DHT, `DHT_UPDATE` y roaming

**Depende de:** B2, B4, B5.

- [ ] Publicar el LocationBlock propio en los K nodos más cercanos y recuperarlo con `FIND_VALUE`.
- [ ] Portar `DHT_UPDATE` firmado a contactos íntimos y aplicar solo bloques con `dhtSeq` mayor y firma válida.
- [ ] Implementar renovación de bloques sin superar 60 días ni permitir saltos de secuencia abusivos.
- [ ] Portar `DHT_QUERY`/`DHT_RESPONSE`/`DHT_EXCHANGE`, vecinos y verificación por clave pública.
- [ ] Redescubrir la dirección tras cambio de red en menos de 60 s.
- [ ] Probar bloque antiguo, bloque de otra clave, bloque expirado, secuencia repetida, salto abusivo y roaming Desktop↔móvil.

**Salida:** tras cambiar de red, Desktop encuentra la nueva dirección del móvil por DHT y el móvil encuentra la de Desktop, sin aceptar un bloque retrocedido.

### B7 — Integración en `:yggdrasil` y persistencia

**Depende de:** B6.

- [ ] Ejecutar el trabajador DHT dentro del servicio, no en React; sobrevivir a `onTaskRemoved` y al cierre de la UI.
- [ ] Persistir contactos verificados, `dhtSeq`, `deviceId` y bloques propios con límites y migración.
- [ ] Programar refresco de buckets y republicación con presupuesto de batería/datos; suspender cuando el usuario desactive la conexión.
- [ ] Exponer estado observable: `nodeId`, contactos, bootstrapped, último bootstrap, valores almacenados, paquetes enviados/recibidos y último error, sin secretos.
- [ ] Probar UI cerrada, reinicio del servicio, cambio de red y parada manual.

**Salida:** con la UI cerrada el DHT sigue operativo, se recupera tras reinicio y la UI muestra un estado que distingue “servicio activo” de “DHT saludable”.

### B8 — Interoperabilidad y endurecimiento

**Depende de:** B7.

- [ ] Añadir DHT al harness Desktop↔móvil: bootstrap, `FIND_NODE`, `STORE`, `FIND_VALUE`, LocationBlock, `DHT_UPDATE` y roaming.
- [ ] Ejecutar fuzzing de códec y pruebas de amplificación, replay, Sybil básico y contactos maliciosos.
- [ ] Medir batería, datos y latencia de bootstrap en dispositivo real durante 24 h.
- [ ] Verificar migración de estado y ausencia de secretos en logs.
- [ ] Solo después habilitar sincronización firmada de perfil, punteros de vault y presencia.

**Salida:** pruebas mixtas repetibles, límites medidos y ningún bloqueo de UI; a partir de aquí se abre la paridad de producto.

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

**No hacer todavía:** grupos, archivos, llamadas y sincronización de perfil. DHT ya es el siguiente bloqueo y se rige por B0–B8; no se adelanta Kademlia sin cerrar B0–B3.

### P2 — Protocolo headless persistente (chat con contacto existente completado; DHT es el siguiente bloqueo)

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
8. [ ] DHT Kademlia, bootstrap y roaming. Ejecutar B1–B8 en orden; B0 está cerrada y B1 es el bloqueo inmediato.
9. [ ] Paridad social, vault, grupos, archivos y llamadas (fases 4–10, solo tras B8).

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

| Tipo | Campos y límites vigentes en Desktop |
|---|---|
| `DHT_PING` | `type`; `queryId` opcional sin límite propio. Se acota por el frame máximo de 10 MiB. |
| `DHT_PONG` | `nodeId` opcional para el validador, 1-128 hex; el handler espera el nodo. `queryId` opcional. |
| `DHT_FIND_NODE` | `targetId` 1-128 hex; `queryId` opcional. |
| `DHT_FOUND_NODES` | `nodes` obligatorio, máximo 20; cada `upeerId` 1-128 hex y `address` de hasta 100 caracteres. `publicKey` y `nodeId` aún no se validan. |
| `DHT_FIND_VALUE` | `key` de 40 o 64 hex; `queryId` opcional. |
| `DHT_FOUND_VALUE` | `key` opcional de 40/64 hex; exige `value` o `nodes`; `value` serializado máximo 10 000 caracteres. `publisher`, `timestamp` y `signature` aún no se validan. |
| `DHT_STORE` | `key` de 40/64 hex; `value` serializado máximo 10 000 caracteres; `ttl` opcional entre 0 y 2 592 000 s. |
| `DHT_STORE_ACK` | `key` de 40/64 hex; `queryId` opcional. |
| `DHT_QUERY` | `targetId` 32-128 hex según `isValidHexId`; `referralContext` no se valida todavía. |
| `DHT_RESPONSE` | `targetId` 32-128 hex; `locationBlock` opcional pero validado si existe; `neighbors` debe ser array si aparece. |
| `DHT_UPDATE` | `locationBlock` obligatorio: `address` string, `dhtSeq` entero no negativo, `signature` de 128 hex y `powProof` de hasta 256 caracteres si existe. |
| `DHT_EXCHANGE` | `peers` obligatorio, máximo 50; cada `upeerId` 32-128 hex y `publicKey` de 64 hex; `locationBlock` opcional validado. |

- Todo paquete viaja en un frame de como máximo 10 MiB. Los límites de valor son caracteres del JSON serializado, no bytes.
- Los límites de `queryId`, `publisher`, `timestamp`, `publicKey` y `nodeId` de nodos siguen abiertos para B3; no se deben relajar por conveniencia.

### Errores esperados

- JSON raíz no objeto, datos trailing, número no entero, firma distinta de 64 bytes, clave pública distinta de 32 bytes y `senderUpeerId` que no deriva de la clave.
- `senderYggAddress` que no es IPv6 de Yggdrasil, `type` ausente, `signature`/`senderUpeerId`/`senderYggAddress` inyectados por el llamador, bloque expirado, `dhtSeq` negativo, dirección inválida o lista de direcciones vacía.
- Un paquete firmado manipulado se rechaza antes de ejecutar handlers. Un `DHT_UPDATE` con firma exterior válida pero bloque interior inválido no debe aceptarse como actualización de ubicación.

## Fases de producto (solo después de B8)

B0–B8 son la secuencia obligatoria para red y descubrimiento. Las fases siguientes describen producto y no deben adelantarse para compensar una salida incompleta de B8. Las fases de identidad, transporte e integración Android ya tienen su estado en B0–B7.

### 4. Contactos y chat directo mínimo E2EE

Portar handshake (incluido `powProof` Argon2id para contactos nuevos), X3DH, signed pre-keys, conversión Ed25519/Curve25519, sealed boxes, Double Ratchet, persistencia transaccional, ACK, DR_RESET y outbox. Habilitar contactos y una conversación de texto vertical de extremo a extremo antes de sumar funciones de chat.

**Estado actual:** parcial. Las primitivas están portadas, el ratchet se persiste, el servicio descifra `CHAT` con X3DH, envía ACK, entrega mensajes salientes con outbox, notifica mensajes entrantes y la UI muestra la conversación. `DR_RESET`, SPK obsoleta, misma SPK idempotente por `resetId`, ACK perdido, duplicados, peer inalcanzable, reconexión de red, rate limiting y replay idempotente están probados. Queda pendiente el alta de un contacto nuevo desde móvil: `HANDSHAKE_REQ` no incluye el `powProof` Argon2id que Desktop exige para contactos nuevos, ni `addresses`/`ephemeralPublicKey`; el chat cruzado actual usa un contacto ya conocido. También faltan cambio de identidad del contacto y offline prolongado.

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

Ubicar cada petición en la primera fase B incompleta de la que dependa. Una propiedad de identidad puede tener UI y persistencia local antes de B6, pero su sincronización con otros nodos no se considera terminada hasta B8. No empezar la fase dependiente si la salida anterior no está demostrada con pruebas.

## Anexo — Inventario de requisitos por área

Este anexo conserva el detalle histórico de las fases 0–15. Sirve para consultar requisitos y criterios de salida, no para decidir el orden: el orden vigente es B0–B8 y, después, las fases de producto 4–10.

| Bloque vigente | Requisitos detallados en el anexo |
|---|---|
| B0 | Fases 0, 1 y 4: alcance, contratos, vectores, identidad |
| B1 | Fases 1, 4 y 7: canonicalización, firmas, paquetes |
| B2 | Fases 4 y 7: identidad, LocationBlock, secuencia |
| B3 | Fase 7: códec, límites y admisión |
| B4 | Fases 1 y 7: DHT Kademlia y protocolo |
| B5 | Fases 2 y 7: bootstrap, descubrimiento y red |
| B6 | Fase 7: location blocks, `DHT_UPDATE` y roaming |
| B7 | Fases 3, 5, 6 y 7: app nativa, persistencia y servicio `:yggdrasil` |
| B8 | Fases 2, 7 y 15: interoperabilidad, fuzzing y pruebas |
| Producto 4 | Fases 4, 8 y 9: identidad, UI, contactos y chat |
| Producto 5 | Fase 9: paridad de chat y notificaciones |
| Producto 6 | Fases 5 y 10: persistencia, vault y multidispositivo |
| Producto 7 | Fase 11: grupos y roles |
| Producto 8 | Fase 12: archivos, multimedia y notas de voz |
| Producto 9 | Fase 13: llamadas y compartir pantalla |
| Producto 10 | Fases 14 y 15: seguridad, backup, beta y publicación |

> **Aviso:** las casillas de este anexo reflejan el inventario original. No se reinterpretan como estado actual; el estado comprobado está en la tabla superior y en las fases B0–B8.

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

> La parte DHT de esta fase se ejecuta en el orden bloqueante B0–B8 definido en este mismo documento. No se marca ninguna tarea DHT como completada por tener prototipos aislados: cada una exige vector Desktop y prueba cruzada.

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

Portar recuperación offline y continuidad multidispositivo.

#### Tareas

- [ ] Portar vault, renovación de tokens y punteros DHT.
- [ ] Portar recuperación de mensajes y eventos de edición, borrado, reacciones y lectura.
- [ ] Portar recuperación segmentada de adjuntos y su deduplicación.
- [ ] Añadir límites configurables de datos, almacenamiento, batería y replicación.
- [ ] Portar dispositivos propios, confianza y sincronización diferencial de historial.
- [ ] Resolver conflicto de orden, duplicados, expiración, eliminación y rotación de claves.
- [ ] Mostrar claramente pendiente, almacenado en vault, recuperado y entregado.
- [ ] Probar largos periodos offline, cambio de IP, reinicio y recuperación de archivos grandes.

#### Criterios de salida

- [ ] Mensajes y eventos se recuperan móvil↔desktop sin pérdida ni duplicados visibles.
- [ ] La recuperación de adjuntos respeta hashes y cuotas.
- [ ] Reiniciar el servicio no invalida el estado persistido.

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
2. Si es una fase B, comprobar que la anterior tiene su criterio de salida demostrado; si es de producto, confirmar que B8 está cerrada.
3. Revisar dependencias y criterios de salida de fases previas.
4. Crear tareas pequeñas con archivos, interfaces y pruebas esperadas.
5. Mantener este roadmap como fuente de alcance; registrar cambios de decisión aquí.
5. Al terminar, marcar solo tareas verificadas, añadir evidencia de pruebas y anotar limitaciones o riesgos nuevos.