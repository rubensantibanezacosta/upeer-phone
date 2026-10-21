# Roadmap móvil uPeer con Expo

_Estado: planificación inicial. No representa funcionalidades ya implementadas._

> La secuencia de implementación vigente está en [`roadmap/implementation-order.md`](roadmap/implementation-order.md). Ese orden prevalece sobre los números de fase y la secuencia de MVP de este catálogo; las fases siguientes conservan requisitos y criterios de salida, no indican por sí solas qué debe portarse primero.

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

## Fase 0 — Alcance, decisiones y viabilidad de distribución

### Objetivo

Resolver prioridades del producto y restricciones de plataforma antes de construir la aplicación completa.

### Tareas

- [ ] Decidir si el primer objetivo es Android, Android+iOS o una prueba técnica de ambos.
- [ ] Decidir si el cliente se distribuirá por Google Play, App Store, canales privados o varios.
- [ ] Definir si la conexión debe funcionar solo con la app en primer plano o también mientras está en segundo plano.
- [ ] Decidir qué funciones son imprescindibles para el primer lanzamiento: chat directo, grupos, vault, archivos, voz y llamadas.
- [ ] Definir los estados de conexión: desconectado, solicitando permiso, conectando, conectado, reconectando, pausado, detenido por el sistema y error recuperable.
- [ ] Definir que "conectado" requiere motor operativo, peers alcanzables y transporte funcional, no solo servicio activo.
- [ ] Revisar requisitos de Google Play para servicios Android de red en segundo plano y foreground services; revisar requisitos Apple solo si iOS y su integración de red entran en alcance.
- [ ] Especificar divulgación de actividad de red, datos, permisos, notificaciones y desconexión manual; no declarar VPN salvo que la arquitectura técnica finalmente la requiera.
- [ ] Crear matriz de riesgos, dispositivos objetivo y criterios de éxito medibles.

### Criterios de salida

- [ ] Aprobado el alcance del MVP y la prioridad de plataformas.
- [ ] Confirmados los requisitos de distribución, firmas y entitlements.
- [ ] Aprobado el diseño de conexión transparente, visible y revocable.
- [ ] No se promete ejecución móvil permanente ni inmunidad frente al sistema operativo.

## Fase 1 — Inventario y separación del núcleo compartido

### Objetivo

Identificar qué código puede compartirse y especificar contratos entre JavaScript, React Native y las capas nativas.

### Tareas

- [ ] Clasificar módulos desktop como reutilizables, abstraíbles, portables con adaptación o nativos/no portables.
- [ ] Inventariar dependencias de `node:net`, `node:dgram`, `child_process`, `fs`, `Buffer`, Electron IPC y APIs Chromium.
- [ ] Inventariar bindings nativos: `sodium-native`, SQLite/SQLCipher, FFmpeg, WebCodecs y herramientas de empaquetado.
- [ ] Definir paquetes compartidos para protocolo, identidad, criptografía, DHT, dominio y tipos.
- [ ] Definir contratos `YggdrasilService`, `P2PTransport`, `SecureStorage`, `EncryptedDatabase`, `Notifications` y `MediaCapture`.
- [ ] Crear vectores de prueba desktop para BIP39/derivación, upeer ID, canonicalización, firmas, cifrado, ratchet, archivos y paquetes de grupo/vault.
- [ ] Ejecutar vectores desde Node y establecer cómo se ejecutarán desde Android e iOS.
- [ ] Documentar versionado de protocolo, detección de capacidades y estrategia de migración.
- [ ] Definir límites de memoria y validación para paquetes externos.

### Criterios de salida

- [ ] Existe un mapa de módulos y dependencias revisado.
- [ ] Los contratos de plataforma están versionados y probados con implementaciones mock.
- [ ] Los vectores de interoperabilidad están guardados y tienen resultados conocidos del cliente desktop.
- [ ] Se identifica cualquier módulo cuya licencia o dependencia impida su uso móvil.

## Fase 2 — Prueba técnica del motor Yggdrasil y Go

### Objetivo

Comprobar en hardware real si las integraciones móviles de Yggdrasil pueden soportar el transporte y el ciclo de vida requerido por uPeer.

### Android

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

### iOS, si está en alcance

- [ ] Revisar el proyecto oficial `yggdrasil-ios`, API Swift, framework Go y extensión `NetworkExtension`.
- [ ] Confirmar App Groups, entitlements, cuenta de organización y provisioning profiles requeridos.
- [ ] Probar ciclo de vida de app y extensión, suspensión, expiración, reconexión y comunicación entre procesos.
- [ ] Confirmar que el diseño es compatible con las capacidades de red y distribución previstas.

### Decisión de empaquetado

- [ ] Preferir biblioteca/framework móvil compilado desde Go frente a empaquetar un ejecutable Go independiente.
- [ ] Comparar upstream oficial, fork mínimo o bindings propios según API, sockets, mantenimiento y licencia.
- [ ] Definir actualizaciones de upstream, parches, builds reproducibles y revisión de seguridad.
- [ ] Verificar compatibilidad entre API del motor móvil, `yggstack` desktop y transporte actual de uPeer.

### Criterios de salida

- [ ] Un dispositivo Android físico conecta a la red Yggdrasil y expone el estado de forma fiable.
- [ ] Un nodo desktop puede intercambiar al menos un flujo de prueba a través de la integración elegida.
- [ ] Se documenta la API de transporte disponible, la ruta de los sockets TCP/UDP de uPeer y cómo se evita routing recursivo sin tunelizar tráfico ajeno a Yggdrasil.
- [ ] Si iOS es requisito, existe una prueba equivalente y confirmación de entitlements.
- [ ] Se toma una decisión documentada de reutilización o extensión del wrapper móvil.

## Fase 3 — Aplicación Expo, módulos nativos y CI

### Objetivo

Crear la base de la app y un pipeline capaz de compilar y probar código nativo, sin depender de Expo Go.

### Tareas

- [ ] Crear `apps/mobile` con Expo, React Native y TypeScript.
- [ ] Configurar Expo development client, development builds y prebuild/CNG.
- [ ] Crear módulos Expo locales para los bridges Android/iOS.
- [ ] Automatizar config plugins para permisos, servicios, extensiones, entitlements y configuración nativa.
- [ ] Crear bridge tipado con comandos, eventos, errores, timeout y versión de API.
- [ ] Añadir mocks de módulo nativo para desarrollo UI y pruebas unitarias.
- [ ] Configurar CI con versiones fijadas de Node, Go, SDK/NDK, Java y Xcode.
- [ ] Compilar artefactos nativos desde fuente y verificar sus hashes en CI.
- [ ] Crear builds de debug, QA y release firmadas según cada plataforma.
- [ ] Verificar que la app instalada funciona sin Metro.

### Criterios de salida

- [ ] La build Expo instala y se comunica con el módulo nativo en dispositivo físico.
- [ ] La build de CI es reproducible dentro de los límites definidos.
- [ ] Los cambios generados por prebuild no requieren edición manual sin automatizar.
- [ ] El estado de errores del módulo nativo se refleja correctamente en JavaScript.

## Fase 4 — Identidad y custodia de claves

### Objetivo

Crear/importar identidades interoperables sin exponer material privado al renderer.

### Tareas

- [ ] Portar generación e importación de frase BIP39 de 12 palabras, validación y confirmación de respaldo.
- [ ] Preservar derivación exacta de Ed25519/X25519 y upeer ID del desktop.
- [ ] Implementar operaciones privadas en módulo nativo o backend de seguridad aislado.
- [ ] Guardar claves con Android Keystore/Keychain según capacidades disponibles.
- [ ] Proteger material local con claves ligadas al dispositivo cuando sea viable.
- [ ] Implementar bloqueo/desbloqueo por PIN y biometría como control local de acceso.
- [ ] Implementar flujo de revelado de semilla autenticado y con limpieza de memoria/UI.
- [ ] Revisar clipboard, screenshots, logs, backups automáticos y pantalla de tareas recientes.
- [ ] Probar importación móvil→desktop y desktop→móvil con los vectores de la Fase 1.
- [ ] Probar reinstalación, cambio de dispositivo, rotación de claves y recuperación.

### Criterios de salida

- [ ] Una identidad creada/importada en móvil conserva el mismo upeer ID que desktop.
- [ ] Firmas y operaciones criptográficas pasan los vectores cruzados.
- [ ] Las claves privadas no aparecen en logs ni estado serializado de React.
- [ ] El bloqueo impide operaciones sensibles desde la app.

## Fase 5 — Persistencia cifrada, migraciones y archivos locales

### Objetivo

Portar almacenamiento y estado de sesión a una base de datos compatible y protegida en móvil.

### Tareas

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

### Criterios de salida

- [ ] Datos sensibles permanecen cifrados en reposo según la arquitectura aprobada.
- [ ] Migraciones son idempotentes y recuperables.
- [ ] No se pierden estado de ratchet ni mensajes tras terminación abrupta.
- [ ] Existen límites visibles para almacenamiento de blobs.

## Fase 6 — Servicio Yggdrasil en segundo plano en Android

### Objetivo

Mantener la participación voluntaria del nodo Yggdrasil y la comunicación uPeer en Android mediante mecanismos del sistema que correspondan al uso real. Esta fase no presupone una VPN ni el enrutamiento del tráfico general del teléfono.

### Tareas

- [x] Prototipar servicio Android visible en proceso separado para el bridge Yggdrasil, con Binder IPC para el contrato TCP/UDP existente y acción de desconexión en la notificación.
- [x] Guardar la configuración Yggdrasil de restauración cifrada con Android Keystore; no persistir semillas/configuración en texto claro.
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

### Criterios de salida

- [ ] El usuario sabe cuándo el nodo y el transporte uPeer están activos y puede detenerlos desde la app.
- [ ] La app no declara conectividad solo por tener un servicio activo.
- [ ] El routing y el tráfico normal del dispositivo no relacionado con uPeer/Yggdrasil no se redirigen.
- [ ] Cambios de red producen reconexión medible o un error recuperable y visible.
- [ ] No se promete que el sistema nunca finalizará el proceso.

## Fase 7 — Transporte P2P, DHT e interoperabilidad

### Objetivo

Conectar móviles con nodos actuales y conservar formatos de paquetes.

### Tareas

- [ ] Implementar transport nativo o adaptador sobre sockets/streams disponibles del motor móvil.
- [ ] Mantener los formatos de uPeer transportando paquetes IPv6 desde sockets TCP/UDP normales mediante la pila gVisor de Yggstack, salvo que la prueba de interoperabilidad demuestre una alternativa compatible.
- [ ] Resolver explícitamente TCP entrante, UDP, `-remote-tcp`, `-remote-udp`, SOCKS5 y UDP associate.
- [ ] Portar framing, fragmentación, reensamblado, ACKs, timeouts, reintentos y control de congestión.
- [ ] Portar Kademlia: routing table, buckets, find node/value, exchange, bootstrap, expiración y renovación.
- [ ] Portar bootstrap diversificado y documentar DNS/LAN discovery según capacidades móviles.
- [ ] Desactivar o degradar multicast LAN cuando permisos, OS o red no permitan su uso.
- [ ] Portar location blocks, `DHT_UPDATE` firmado, roaming y rediscovery.
- [ ] Limitar frecuencia de heartbeats, presencia y DHT para batería/datos.
- [ ] Validar tipos de paquete, tamaño, firma, rate limiting y límites de memoria.
- [ ] Añadir diagnósticos de peers, RTT, bytes, errores y última reconexión sin datos secretos.

### Criterios de salida

- [ ] Chat de prueba Android↔desktop completa handshake y transporte.
- [ ] Dos móviles pueden descubrirse y comunicarse por la misma red.
- [ ] Cambios de interfaz no duplican ni corrompen mensajes.
- [ ] Paquetes inválidos se rechazan sin consumo descontrolado de CPU/memoria.

## Fase 8 — Diseño y navegación móvil

### Objetivo

Crear una experiencia táctil nativa en lugar de reducir la interfaz desktop.

### Tareas

- [ ] Definir navegación principal: chats, contactos, grupos, llamadas y ajustes según alcance.
- [ ] Diseñar onboarding, importación/creación de identidad y estado de red.
- [ ] Diseñar lista de conversaciones, búsqueda, chat, perfiles, seguridad y ajustes.
- [ ] Adaptar gestos, teclado, safe areas, orientación, tamaños de texto y lector de pantalla.
- [ ] Implementar temas y tokens visuales compartibles; no portar widgets Joy UI/MUI a React Native.
- [ ] Diseñar estados vacíos, offline, reconexión, mensajes pendientes y fallos de permiso.
- [ ] Añadir navegación profunda desde notificaciones a chat/llamada.
- [ ] Validar uso con una mano, accesibilidad y escalas de fuente.

### Criterios de salida

- [ ] Flujos principales funcionan sin depender del layout desktop.
- [ ] Estados de red y seguridad se entienden sin términos internos innecesarios.
- [ ] Accesibilidad básica verificada en ambas plataformas objetivo.

## Fase 9 — Identidad social, mensajería y notificaciones

### Objetivo

Entregar el núcleo de chat directo seguro con persistencia y reintentos.

### Tareas

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

### Criterios de salida

- [ ] Mensajes directos E2EE pasan pruebas móviles↔desktop.
- [ ] Reiniciar app no pierde mensajes pendientes ni estado ratchet.
- [ ] Las notificaciones respetan preferencias de privacidad.
- [ ] La presencia no requiere actividad innecesaria en segundo plano.

## Fase 10 — Vault, offline y sincronización entre dispositivos

### Objetivo

Portar recuperación offline y continuidad multidispositivo.

### Tareas

- [ ] Portar vault, renovación de tokens y punteros DHT.
- [ ] Portar recuperación de mensajes y eventos de edición, borrado, reacciones y lectura.
- [ ] Portar recuperación segmentada de adjuntos y su deduplicación.
- [ ] Añadir límites configurables de datos, almacenamiento, batería y replicación.
- [ ] Portar dispositivos propios, confianza y sincronización diferencial de historial.
- [ ] Resolver conflicto de orden, duplicados, expiración, eliminación y rotación de claves.
- [ ] Mostrar claramente pendiente, almacenado en vault, recuperado y entregado.
- [ ] Probar largos periodos offline, cambio de IP, reinicio y recuperación de archivos grandes.

### Criterios de salida

- [ ] Mensajes y eventos se recuperan móvil↔desktop sin pérdida ni duplicados visibles.
- [ ] La recuperación de adjuntos respeta hashes y cuotas.
- [ ] Reiniciar el servicio no invalida el estado persistido.

## Fase 11 — Grupos y roles

### Objetivo

Portar administración y mensajería grupal con seguridad de miembros.

### Tareas

- [ ] Crear grupos, invitar, aceptar, salir y gestionar membresía.
- [ ] Portar mensajes grupales E2EE, roles admin/miembro y transferencia de administración.
- [ ] Portar cambio de nombre/avatar y actualización de epoch/claves.
- [ ] Aplicar rotación de claves al entrar/salir/expulsar miembros.
- [ ] Portar fanout con concurrencia limitada, retries y modo offline.
- [ ] Portar invitaciones y controles antiabuso/reputación.
- [ ] Probar grupos mixtos de móvil y desktop, miembro offline y cambio de administrador.

### Criterios de salida

- [ ] Operaciones de grupo se mantienen consistentes en varios clientes.
- [ ] Un miembro revocado no puede descifrar mensajes posteriores según el protocolo.
- [ ] Fanout y recovery no producen tormentas de red.

## Fase 12 — Transferencias, multimedia y notas de voz

### Objetivo

Portar adjuntos cifrados usando APIs móviles de selección, almacenamiento y reproducción.

### Tareas

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

### Criterios de salida

- [ ] Archivos y voces interoperan con desktop y verifican hash/firma.
- [ ] Interrupción no deja temporales sensibles huérfanos.
- [ ] Los permisos se piden en contexto y se tolera selección limitada.

## Fase 13 — Llamadas de voz/vídeo y compartir pantalla

### Objetivo

Portar gradualmente llamadas, sustituyendo dependencias específicas de Chromium.

### Tareas

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

### Criterios de salida

- [ ] Audio 1:1 probado durante llamadas prolongadas en dispositivos reales.
- [ ] Interrupciones de sistema y cambios de red tienen resultado predecible.
- [ ] Vídeo/grupos no se consideran completos por la sola presencia de UI.
- [ ] Riesgos de confidencialidad del relay están resueltos o claramente excluidos.

## Fase 14 — Ajustes, seguridad, privacidad y backup

### Objetivo

Completar capacidades auxiliares y endurecer el manejo de datos móviles.

### Tareas

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

### Criterios de salida

- [ ] Preferencias sensibles se aplican en UI, almacenamiento y notificaciones.
- [ ] Se conoce qué datos sobreviven backup, exportación y desinstalación.
- [ ] La cadena de suministro de Go y módulos nativos está documentada.

## Fase 15 — Pruebas, beta, publicación y mantenimiento

### Objetivo

Validar el comportamiento real en redes y dispositivos antes de un lanzamiento general.

### Tareas

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

### Criterios de salida

- [ ] No hay defectos críticos de seguridad/interoperabilidad abiertos.
- [ ] Las pruebas de hardware cumplen umbrales acordados para reconexión, batería y estabilidad.
- [ ] Los permisos y el uso del servicio de red en segundo plano están divulgados y aprobados para el canal de distribución.
- [ ] Existe soporte, monitoreo y procedimiento de actualización de motor Go.

## Secuencia recomendada de entregas

### MVP 1 — Android y chat seguro

- Android físico con motor Yggdrasil y transporte uPeer interoperable; sin tunelizar tráfico general.
- [ ] Crear/importar identidad interoperable.
- [ ] Contactos y chat directo E2EE.
- [ ] Persistencia, outbox básica y notificaciones locales.
- [ ] Interoperabilidad móvil↔desktop validada.

### MVP 2 — Paridad social y multimedia

- [ ] Grupos, roles, edición, reacciones y lectura.
- [ ] Vault y recuperación/sincronización.
- [ ] Archivos cifrados y notas de voz.
- [ ] Ajustes de privacidad, diagnóstico y almacenamiento.

### MVP 3 — Llamadas e iOS

- [ ] Audio 1:1 robusto; después vídeo y grupos.
- [ ] iOS con Network Extension sujeto a viabilidad de entitlements y distribución.
- [ ] Refinamiento de diagnóstico, backup y experiencia de segundo plano.

No condicionar el MVP inicial a llamadas grupales ni a iOS antes de cerrar la prueba de viabilidad móvil. El riesgo principal es integrar el motor Go con sockets de overlay TCP/UDP y el ciclo de vida Android; si el binding solo entrega paquetes IPv6, habrá que medir y escoger una adaptación de red acotada antes de implementar pantallas completas.

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

## Cómo iniciar cada fase

Al empezar una fase:

1. Confirmar su estado con código y pruebas actuales.
2. Revisar dependencias y criterios de salida de fases previas.
3. Crear tareas pequeñas con archivos, interfaces y pruebas esperadas.
4. Mantener este roadmap como fuente de alcance; registrar cambios de decisión aquí.
5. Al terminar, marcar solo tareas verificadas, añadir evidencia de pruebas y anotar limitaciones o riesgos nuevos.