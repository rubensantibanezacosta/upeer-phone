# Harness Desktop ↔ móvil

`desktop-handshake.mjs` envía un `HANDSHAKE_REQ` firmado y framed como Desktop. `accept-handshake.mjs` escucha la respuesta `HANDSHAKE_ACCEPT` y valida su firma, canonicalización y Signed Pre-Key. `send-sealed-chat.mjs` envía un `SEALED`/`CHAT` real con X3DH y el primer mensaje del Double Ratchet.

```sh
UPEER_TARGET=200:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx \
UPEER_SENDER_ADDRESS=200:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy \
node transport-bridge/harness/desktop-handshake.mjs
```

Variables de `desktop-handshake.mjs`:

- `UPEER_TARGET`: dirección Yggdrasil del móvil, obligatoria.
- `UPEER_SENDER_ADDRESS`: dirección Yggdrasil del emisor; debe ser la del nodo que ejecuta yggstack.
- `UPEER_SEED_HEX`: semilla Ed25519 de prueba de 32 bytes.
- `UPEER_SOCKS_HOST` / `UPEER_SOCKS_PORT`: SOCKS5 de Desktop; por defecto `127.0.0.1:9050`.
- `UPEER_DIRECT=1`: conecta directamente, sin SOCKS5.
- `UPEER_PORT`: puerto del listener; por defecto `50005`.
- `UPEER_TIMEOUT_MS`: timeout de conexión; por defecto `10000`.
- `UPEER_TAMPER=1`: altera el alias después de firmar para comprobar que el móvil rechaza la firma.

Para recibir el accept, yggstack debe reenviar el puerto `50005` a un listener local:

```sh
yggstack -useconffile /tmp/yggstack-test.conf \
  -socks 127.0.0.1:9051 -remote-tcp 50005:127.0.0.1:50105

UPEER_LISTEN_PORT=50105 \
UPEER_EXPECT_SENDER_ADDRESS=200:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy \
node transport-bridge/harness/accept-handshake.mjs
```

El listener imprime `verified:true`, el `publicKey` del móvil, su `senderUpeerId`, `spkId` y `spkPub`. Con esos valores se puede enviar un chat sellado:

```sh
UPEER_TARGET=200:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx \
UPEER_SENDER_ADDRESS=200:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy \
UPEER_SOCKS_PORT=9051 \
UPEER_MOBILE_PUBLIC_KEY=<publicKey del accept> \
UPEER_MOBILE_SPK_PUB=<spkPub del accept> \
UPEER_MOBILE_SPK_ID=<spkId del accept> \
UPEER_MESSAGE_COUNT=2 \
node transport-bridge/harness/send-sealed-chat.mjs
```

`UPEER_EXPECT_TYPE=ACK` hace que `accept-handshake.mjs` espere y valide el ACK del móvil; como el ACK no incluye `publicKey`, hay que pasar `UPEER_EXPECT_PUBLIC_KEY=<publicKey del accept>`. En el dispositivo deben aparecer `CHAT descifrado`, `ACK enviado` y, tras reiniciar el servicio, `Estado del protocolo cargado` con las sesiones y mensajes persistidos.

`send-sealed-chat.mjs` aplica la misma compresión que Desktop a `UPEER_MESSAGE`: a partir de 512 caracteres UTF-16 usa gzip + base64 con el prefijo `❣UP❣` si reduce el tamaño. La salida informa `messageBytes` y `compressed`; el móvil debe persistir y notificar el texto descomprimido. Para capturar la SPK del móvil con `accept-handshake.mjs`, usa `UPEER_EXPECT_TYPE=HANDSHAKE_REQ`.

## DR_RESET y outbox

`send-dr-reset.mjs` firma un `DR_RESET` con una SPK nueva y un `resetId` UUID nuevo y, si se indica `UPEER_LISTEN_PORT`, escucha el chat que el móvil reconstruye desde su outbox. Ese chat debe usar exactamente `spkId` / `spkPub` del reset y descifrarse con la clave derivada, de modo que un fallo al limpiar la sesión o al reintentar el outbox haga fallar la prueba.

```sh
UPEER_TARGET=200:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx \
UPEER_SENDER_ADDRESS=200:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy \
UPEER_SEED_HEX=<semilla del contacto Desktop> \
UPEER_SPK_ID=1792605000000 \
UPEER_LISTEN_PORT=50105 \
UPEER_EXPECT_MOBILE_CHAT=1 \
node transport-bridge/harness/send-dr-reset.mjs
```

Para comprobar que una SPK obsoleta no reemplaza a la nueva, añade `UPEER_STALE_SPK_ID=<id anterior>` y `UPEER_ACCEPT_HANDSHAKE=0`. El script envía primero la SPK nueva y después la obsoleta; el chat del outbox debe seguir descifrándose con la nueva. Con `UPEER_STALE_HANDSHAKE=1` reenvía además un `HANDSHAKE_REQ` firmado con la SPK obsoleta. El móvil acepta un `DR_RESET` con `spkId` igual o mayor que el actual; el mismo `spkId` no reemplaza la SPK pero sí reinicia el ratchet, y un `spkId` menor se ignora. El `resetId` UUID firmado se deduplica por peer para que un reset repetido no vuelva a reiniciar; un `DR_RESET` legacy sin `resetId` se acepta pero no se deduplica. En `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT` la SPK solo se actualiza si es más reciente. `UPEER_RATCHET_CONTEXT` acepta `direct` o `group:<id>` (sin `:` adicionales).

Con `UPEER_SKIP_ACK_FIRST=1` y `UPEER_EXPECT_MESSAGES=2`, `send-dr-reset.mjs` omite el primer ACK: el móvil debe reenviar el mismo `id` tras el timeout de 5 s y el harness debe cerrar con `CHAT_DUPLICATE` y `verified:true`.

El outbox móvil entrega en paralelo por peer: una entrada hacia un peer inalcanzable no bloquea mensajes a otros. Tras 30 intentos, el mensaje se marca `failed` y sale del outbox. Esto permite limpiar entradas antiguas de pruebas sin reiniciar la app.

## Rate limiting y replay

El móvil usa el token bucket y los límites de Desktop para `SEALED` 5000/s, `HANDSHAKE_REQ`/`HANDSHAKE_ACCEPT` 50/min, `CHAT` 100/min, `ACK` 200/min y `DR_RESET` 20/min; Desktop aplica también el límite de 20/min para `DR_RESET`. `SEALED` se limita antes de descifrar; el resto, tras validar la firma. Los frames limitados aparecen como `limited` en `inboundStatus` y como `rateLimited` en `protocolStatus`.

`send-handshake-flood.mjs` reenvía un `HANDSHAKE_REQ` firmado más veces que el límite y cuenta los `HANDSHAKE_ACCEPT` recibidos. Con `UPEER_COUNT=120` y el límite por defecto de 50/min, la prueba calcula el techo del token bucket según el tiempo transcurrido y falla si el móvil acepta todo el flood o supera ese techo.

```sh
UPEER_TARGET=200:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx \
UPEER_SENDER_ADDRESS=200:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy \
UPEER_LISTEN_PORT=50105 \
UPEER_SOCKS_PORT=9051 \
UPEER_COUNT=120 \
node transport-bridge/harness/send-handshake-flood.mjs
```

El token bucket permite una ráfaga inicial de 50 y repone 50 tokens por minuto, por lo que la salida incluye `elapsedMs` y `expectedCeiling`. Usa una identidad distinta o espera 60 segundos antes de repetir la prueba. La política de replay exige `id` UUID en `CHAT`/`ACK`: un duplicado del mismo remitente se ACKea sin volver a descifrar, una colisión de `id` entre conversaciones se rechaza y un ACK solo actualiza mensajes propios de ese remitente.

## Pérdida de frames

`send-truncated-frame.mjs` abre una conexión Yggdrasil, envía el prefijo de longitud y solo una parte del payload, y cierra. El servicio debe registrar el cierre, liberar el slot y seguir aceptando frames válidos.

```sh
UPEER_TARGET=200:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx \
UPEER_SOCKS_PORT=9051 \
node transport-bridge/harness/send-truncated-frame.mjs
```

Después del frame truncado, repite un `desktop-handshake.mjs` o `send-dr-reset.mjs` válido contra el mismo dispositivo. `UPEER_TRUNCATE_BYTES` ajusta cuántos bytes se envían; por defecto 6.

## Cambio de red sin reiniciar

Con el dispositivo conectado por USB se puede cortar la red sin perder `adb`:

```sh
adb shell svc wifi disable
adb shell svc data disable
sleep 20
adb shell pidof com.upeer.upeer:yggdrasil
adb shell svc wifi enable
adb shell svc data enable
```

Durante el corte, `getPeerStatus()` debe mostrar los peers en `Up:false` con `network is unreachable`. Tras reactivar la red, el mismo PID debe recuperar peers y un `send-dr-reset.mjs` con listener debe entregar un chat. Restaura siempre Wi-Fi y datos al terminar.

## ACK perdido y duplicados

`receive-mobile-chat.mjs` reconoce un `CHAT` repetido por `id` sin volver a descifrarlo y responde al duplicado. Con `UPEER_SKIP_ACK_FIRST=1` omite el primer ACK: el móvil debe reenviar el mismo `id` tras el timeout de 5 s y el harness termina con `CHAT_DUPLICATE`. `UPEER_SPK_ID` fija una SPK determinista derivada de `UPEER_SEED_HEX`, de modo que se puede enviar primero el `DR_RESET` sin listener y abrir el receptor después con la misma SPK.

```sh
UPEER_LISTEN_PORT=50105 \
UPEER_SENDER_ADDRESS=200:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy:yyyy \
UPEER_SKIP_ACK_FIRST=1 \
UPEER_EXPECT_MESSAGES=2 \
node transport-bridge/harness/receive-mobile-chat.mjs
```
