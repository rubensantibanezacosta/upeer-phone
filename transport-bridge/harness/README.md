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
