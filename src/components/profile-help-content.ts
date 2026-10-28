export type ProfileInfoKey = 'reputacion' | 'almacenamiento' | 'direccion' | 'dispositivos' | 'id' | 'clave' | 'red';

export const profileHelpContent: Record<ProfileInfoKey, { title: string; description: string; items: { label: string; description: string }[] }> = {
  reputacion: {
    title: 'Reputación',
    description: 'La reputación es un criterio técnico interno que puntúa cuentas, no dispositivos ni nodos: la misma cuenta vale lo mismo desde cualquier equipo suyo. Solo se te avisa cuando es muy mala, porque una puntuación normal no es asunto de nadie.',
    items: [
      { label: 'De dónde sale', description: 'De avales firmados que intercambian tus contactos directos: un mensaje entregado, un trozo de mensaje guardado mientras estabas desconectado, o una penalización por comportamiento malicioso o fallo de integridad.' },
      { label: 'Quién cuenta', description: 'Solo los avales de tus contactos directos, durante los últimos 30 días. Un desconocido no puede subir ni hundir tu puntuación.' },
      { label: 'Cuándo se avisa', description: 'Únicamente por debajo de 40 sobre 100. El resto del tiempo no se muestra ninguna nota, ni la tuya ni la de los demás.' },
      { label: 'Para qué se usa', description: 'Para decidir con qué prioridad se procesan los paquetes de una cuenta y para marcar como sospechosa una solicitud de contacto antes de aceptarla.' },
    ],
  },
  almacenamiento: {
    title: 'Almacenamiento cedido',
    description: 'Esta aplicación funciona sin servidores centrales. Para que tus contactos reciban mensajes mientras están desconectados, los nodos de la red P2P (como el tuyo) ceden un pequeño espacio para guardar temporalmente esos datos cifrados.',
    items: [
      { label: 'Privacidad', description: 'Todo el contenido está cifrado de extremo a extremo (E2EE) con llaves que solo el destinatario posee. Tú no puedes leer lo que almacenas, ni nadie puede leer tus mensajes guardados en otros nodos.' },
      { label: 'Cuotas dinámicas', description: 'Invitados: 50 MB. Confiables: 500 MB. Amigos íntimos (Score 80+): hasta 2.5 GB por persona.' },
      { label: 'Caducidad', description: 'Tu nodo prioriza siempre el almacenamiento de tus contactos directos. Los datos expiran automáticamente si no son reclamados en 60 días.' },
    ],
  },
  direccion: {
    title: 'Tu dirección de contacto',
    description: 'Tu dirección de contacto es lo que tienes que compartir con alguien para que pueda escribirte. Es como tu número de teléfono, pero para esta app.',
    items: [
      { label: '¿Cómo se comparte?', description: 'Puedes copiarla y pegarla donde quieras, o mostrar el código QR para que alguien la escanee directamente con su móvil.' },
      { label: '¿Es segura?', description: 'Sí, puedes compartirla libremente. Solo sirve para que te encuentren en la app, no da acceso a tus mensajes ni a tu cuenta.' },
    ],
  },
  dispositivos: {
    title: 'Dispositivos de la cuenta',
    description: 'Son los dispositivos que pueden usar tu cuenta sin que les copies la frase de recuperación. Cada uno guarda su propia clave en el almacén seguro del sistema.',
    items: [
      { label: 'Vincular un dispositivo', description: 'Genera una invitación y ábrela en el dispositivo nuevo. Ese dispositivo crea su clave, te enseña su solicitud y tú firmas su certificado desde aquí.' },
      { label: 'Este dispositivo', description: 'Es el que estás usando ahora. Aparece marcado en la lista para que sepas cuál es cuál cuando haya varios.' },
      { label: 'Qué no viaja', description: 'Ni la frase de recuperación ni la clave de la cuenta salen del dispositivo que autoriza. El dispositivo nuevo solo recibe su certificado.' },
      { label: 'Caducidad', description: 'La invitación caduca a los pocos minutos y solo sirve una vez. Si no la usas, desaparece sola.' },
    ],
  },
  id: {
    title: 'ID de contacto',
    description: 'Tu ID es el nombre único que te identifica dentro de la app. Forma parte de tu dirección de contacto.',
    items: [
      { label: '¿Lo puedo cambiar?', description: 'No. El ID se genera una sola vez cuando creas tu cuenta y no se puede modificar. Es lo que garantiza que eres siempre tú, sin importar desde qué dispositivo te conectes.' },
      { label: '¿Alguien puede verlo?', description: 'Solo quien ya tiene tu dirección de contacto completa. No es público ni aparece en ningún listado.' },
    ],
  },
  clave: {
    title: 'Clave de verificación',
    description: 'Es un código largo que la app usa para demostrar que eres quien dices ser. Nunca tienes que escribirla ni recordarla, la app la gestiona sola.',
    items: [
      { label: '¿Para qué sirve?', description: 'Cuando alguien te manda un mensaje, la app comprueba automáticamente esta clave para asegurarse de que el mensaje llega al destinatario correcto y no ha sido manipulado por nadie.' },
      { label: '¿Puedo compartirla?', description: 'Es pública por diseño: puede verla cualquier contacto tuyo si quiere verificar que realmente está hablando contigo. No permite acceder a tu cuenta.' },
    ],
  },
  red: {
    title: 'Dirección de red',
    description: 'Es la dirección de tu dispositivo dentro de la red que usa la app para conectarse directamente contigo sin pasar por ningún servidor.',
    items: [
      { label: '¿Cambia con el tiempo?', description: 'Sí. Cada vez que te conectas desde una red diferente (casa, trabajo, móvil de datos) puede ser distinta. La app lo gestiona automáticamente.' },
      { label: '¿Tengo que hacer algo?', description: 'No. Solo es informativa. Puedes compartirla si alguien te la pide para conectarse manualmente, pero en condiciones normales la app lo hace sola.' },
    ],
  },
};