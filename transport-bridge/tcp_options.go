package transportbridge

import "net"

// setTCPNoDelay quita la espera de Nagle en una conexión TCP.
//
// Sin esto, un frame pequeño sale del cliente y se queda hasta que el kernel junta más bytes o vence
// el temporizador de Nagle, lo que añade decenas de milisegundos por paquete. En el chat apenas se nota
// porque los mensajes son pocos, pero en la transferencia de ficheros cada tramo espera su turno y el
// retardo se acumula: es una de las razones de que servir un fichero tarde mucho más que escribirlo.
//
// No es un fallo silencioso si no se puede aplicar: la conexión sigue sirviendo, solo con el retardo.
func setTCPNoDelay(connection net.Conn) {
	tcp, ok := connection.(*net.TCPConn)
	if !ok {
		return
	}
	_ = tcp.SetNoDelay(true)
}

// enableKeepAlive marca la conexión para que el sistema detecte un par caído sin esperar a que una
// escritura falle. Se usa junto al NoDelay en las conexiones de fichero.
func enableKeepAlive(connection net.Conn) {
	tcp, ok := connection.(*net.TCPConn)
	if !ok {
		return
	}
	_ = tcp.SetKeepAlive(true)
}
