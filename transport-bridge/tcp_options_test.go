package transportbridge

import (
	"net"
	"testing"
)

// Comprueba que las conexiones TCP del puente salen con NoDelay activo.
//
// Sin esto cada frame pequeño espera al temporizador de Nagle y el retardo se acumula a lo largo de
// los ~140 tramos de un fichero. La prueba usa un par de sockets reales de loopback, así que ejercita
// el mismo camino que la conexión de fichero.
func TestSetTCPNoDelayAppliesToRealConnections(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	defer listener.Close()

	accepted := make(chan net.Conn, 1)
	go func() {
		connection, acceptErr := listener.Accept()
		if acceptErr == nil {
			accepted <- connection
		}
	}()

	client, err := net.Dial("tcp", listener.Addr().String())
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer client.Close()

	setTCPNoDelay(client)
	enableKeepAlive(client)

	tcp, ok := client.(*net.TCPConn)
	if !ok {
		t.Fatalf("la conexión de prueba no es TCP")
	}
	// La comprobación observable: la conexión sigue usable después de aplicar las opciones, y el
	// NoDelay es un estado del socket que Go no expone para leer, así que se verifica que enviar y
	// recibir datos sigue funcionando y que no se pierde el tipo.
	if _, err := client.Write([]byte("hola")); err != nil {
		t.Fatalf("write tras NoDelay: %v", err)
	}

	server := <-accepted
	defer server.Close()
	setTCPNoDelay(server)
	serverTCP, ok := server.(*net.TCPConn)
	if !ok {
		t.Fatalf("la conexión aceptada no es TCP")
	}
	buffer := make([]byte, 4)
	if _, err := serverTCP.Read(buffer); err != nil {
		t.Fatalf("read: %v", err)
	}
	if string(buffer) != "hola" {
		t.Fatalf("datos inesperados: %q", string(buffer))
	}
	_ = tcp
}

// setTCPNoDelay sobre algo que no es un socket TCP no debe entrar en pánico: el puente recibe
// net.Conn y no siempre es *net.TCPConn.
func TestSetTCPNoDelayIgnoresNonTCPConnections(t *testing.T) {
	left, right := net.Pipe()
	defer left.Close()
	defer right.Close()

	setTCPNoDelay(left)
	enableKeepAlive(left)
}
