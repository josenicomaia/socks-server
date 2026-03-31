package br.com.nicomaia.server.commands;

import br.com.nicomaia.server.net.AddressType;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandResponseTest {

    // Concrete subclass for testing the abstract class
    private static class TestCommandResponse extends CommandResponse {
        public TestCommandResponse(Command command, ResponseType responseType) {
            super(command, responseType);
        }
    }

    @Test
    void shouldBuildCorrectResponseBytesForIPv4() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        Command command = new Command((byte) 0x05, CommandType.CONNECT, AddressType.IPV4, address, 8080);
        var response = new TestCommandResponse(command, ResponseType.SUCCEEDED);

        Socket client = mock(Socket.class);
        when(client.getInetAddress()).thenReturn(InetAddress.getByAddress(new byte[]{(byte) 192, (byte) 168, 1, 1}));
        when(client.getPort()).thenReturn(12345);

        byte[] bytes = response.getBytes(client);

        // SOCKS version
        assertEquals((byte) 0x05, bytes[0]);
        // Response type (SUCCEEDED = 0x00)
        assertEquals((byte) 0x00, bytes[1]);
        // Reserved byte
        assertEquals((byte) 0x00, bytes[2]);
        // Address type (IPV4 = 0x01)
        assertEquals((byte) 0x01, bytes[3]);
        // Client IP: 192.168.1.1
        assertEquals((byte) 192, bytes[4]);
        assertEquals((byte) 168, bytes[5]);
        assertEquals((byte) 1, bytes[6]);
        assertEquals((byte) 1, bytes[7]);
        // Port 12345: high byte = 48, low byte = 57
        assertEquals((byte) 48, bytes[8]);
        assertEquals((byte) 57, bytes[9]);
    }

    @Test
    void shouldBuildCorrectResponseBytesForDomainName() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[]{93, 0, 0, 1});
        Command command = new Command((byte) 0x05, CommandType.CONNECT, AddressType.DOMAIN_NAME, address, 443);
        var response = new TestCommandResponse(command, ResponseType.SUCCEEDED);

        Socket client = mock(Socket.class);
        when(client.getInetAddress()).thenReturn(InetAddress.getByAddress(new byte[]{10, 0, 0, 1}));
        when(client.getPort()).thenReturn(443);

        byte[] bytes = response.getBytes(client);

        // SOCKS version
        assertEquals((byte) 0x05, bytes[0]);
        // Response type
        assertEquals((byte) 0x00, bytes[1]);
        // Reserved
        assertEquals((byte) 0x00, bytes[2]);
        // Address type (DOMAIN_NAME = 0x03)
        assertEquals((byte) 0x03, bytes[3]);
        // Domain length indicator (hardcoded 4 in implementation)
        assertEquals((byte) 0x04, bytes[4]);
    }

    @Test
    void shouldEncodePortCorrectly() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        Command command = new Command((byte) 0x05, CommandType.CONNECT, AddressType.IPV4, address, 80);
        var response = new TestCommandResponse(command, ResponseType.SOCKS_SERVER_FAILURE);

        Socket client = mock(Socket.class);
        when(client.getInetAddress()).thenReturn(address);
        when(client.getPort()).thenReturn(443);

        byte[] bytes = response.getBytes(client);

        // Response type should be SOCKS_SERVER_FAILURE = 0x01
        assertEquals((byte) 0x01, bytes[1]);

        // Port 443: high = 1, low = 187
        int port = ((bytes[bytes.length - 2] & 0xFF) << 8) | (bytes[bytes.length - 1] & 0xFF);
        assertEquals(443, port);
    }
}
