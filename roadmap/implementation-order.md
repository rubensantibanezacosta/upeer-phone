# Orden de portado móvil desde Desktop

Este documento define el orden de implementación. `../roadmap.md` conserva el inventario amplio de requisitos; sus números de fase y la antigua secuencia MVP no prevalecen sobre este plan.

## Estado comprobado

- Móvil tiene prototipos de derivación BIP39/uPeer ID, almacenamiento SecureStore, contactos SQLite y arranque/estado del motor Yggdrasil.
- Móvil no tiene el protocolo uPeer entrante completo, validación común de paquetes, DHT Kademlia, handshake interoperable ni chat E2EE.
- `src/domain/desktop-chat-protocol.md` confirma que el bridge actual no tiene listener TCP entrante ni la ruta de respuestas firmadas/sealed requerida por Desktop.
- Desktop ya tiene perfil con alias/avatar, persistencia local de esos campos y publicación firmada; el descubrimiento/propagación presupone su red y DHT.
- La UI de móvil no demuestra interoperabilidad. No mostrar acciones como funcionales hasta que el correspondiente criterio de salida pase pruebas con Desktop.

## Secuencia obligatoria

### 0. Contrato común y pruebas de interoperabilidad

Congelar formatos/versiones de identidad, serialización canónica, firmas, paquetes, límites y errores mediante vectores de Desktop. Documentar qué operaciones criptográficas conserva cada dispositivo. No modificar derivaciones ni crear un protocolo móvil alternativo.

**Salida:** los vectores son reproducibles en los entornos elegidos y existe un harness Desktop↔móvil. Toda fase posterior añade casos a ese harness.

### 1. Identidad y perfil local seguros

Cerrar la identidad ya prototipada: creación, recuperación, desbloqueo, bloqueo, borrado y manejo de secretos; separar claves privadas del estado React. Persistir alias y avatar según el modelo Desktop. Guardar metadatos pequeños en almacenamiento seguro y blobs de imagen en almacenamiento privado de app con límites, migración y borrado. Mostrar dirección de red cuando el nodo la proporcione.

El perfil en esta etapa es **local**. No afirmar que el alias/foto ya se sincroniza. No duplicar la identidad criptográfica en un segundo almacén de perfil.

**Salida:** vectores de identidad pasan; reinicio conserva identidad y perfil; recuperación y borrado funcionan; ninguna clave privada se serializa en UI/logs.

### 2. Transporte real y ciclo de vida Android

Completar sockets de overlay para los puertos/protocolos de Desktop: recepción TCP, respuestas, framing, límites, timeouts, cierre y UDP requerido. Integrarlos con el servicio Android visible y la reconexión. La prueba de eco existente no equivale a transporte uPeer.

**Salida:** Desktop y móvil intercambian frames reales en ambos sentidos; pruebas cubren fragmentación/coalescencia, pérdida, reinicio y cambios de red; servicio y salud del transporte se distinguen.

### 3. Núcleo de red autenticado y descubrimiento

Portar validación de paquetes, canonicalización/firma, rate limiting y replay protection; después Kademlia, bootstrap, location blocks, `DHT_UPDATE`, renovación y roaming. Reutilizar algoritmos/formatos Desktop y sus límites.

**Aquí se habilita la sincronización firmada del perfil:** emitir el mismo `IDENTITY_UPDATE` firmado, publicarlo en DHT y verificarlo/guardarlo al recibirlo. No implementarlo como envío UDP ad hoc antes de disponer del receptor autenticado y del descubrimiento.

**Salida:** pruebas Desktop↔móvil de paquetes válidos e inválidos, replay, secuencia DHT y cambios de dirección; cambios de alias/avatar llegan a otro dispositivo y se restauran desde DHT.

### 4. Contactos y chat directo mínimo E2EE

Portar handshake, X3DH, signed pre-keys, conversión Ed25519/Curve25519, sealed boxes, Double Ratchet, persistencia transaccional, ACK, DR_RESET y outbox. Habilitar contactos y una conversación de texto vertical de extremo a extremo antes de sumar funciones de chat.

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