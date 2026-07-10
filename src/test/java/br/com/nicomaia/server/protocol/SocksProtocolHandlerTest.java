package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import br.com.nicomaia.server.net.AddressResolver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class SocksProtocolHandlerTest {

  private static Socket mockSocket(byte[] input, ByteArrayOutputStream output) throws IOException {
    Socket socket = mock(Socket.class);
    when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(input));
    when(socket.getOutputStream()).thenReturn(output);
    when(socket.isClosed()).thenReturn(false);
    return socket;
  }

  private static SocksProtocolHandler newHandler() {
    return new SocksProtocolHandler(mock(AddressResolver.class), mock(HandlersHolder.class));
  }

  @Test
  void shouldRejectUnsupportedSocksVersionWithoutReplying() throws IOException {
    // SOCKS4 hello: version 0x04, 1 method, NO_AUTH
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Socket socket = mockSocket(new byte[] {0x04, 0x01, 0x00}, output);

    newHandler().handle(socket);

    assertEquals(0, output.size(), "Server must not reply to a non-SOCKS5 hello");
  }

  @Test
  void shouldRejectWhenNoAcceptableMethodsOffered() throws IOException {
    // SOCKS5 hello offering only USERNAME (0x02), never NO_AUTH
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Socket socket = mockSocket(new byte[] {0x05, 0x01, 0x02}, output);

    newHandler().handle(socket);

    assertArrayEquals(
        new byte[] {0x05, (byte) 0xFF},
        output.toByteArray(),
        "Server must reply NO ACCEPTABLE METHODS (0xFF)");
  }

  @Test
  void shouldAcceptNoAuthHello() throws IOException {
    // SOCKS5 hello offering NO_AUTH; command phase left empty so handling stops after auth.
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Socket socket = mockSocket(new byte[] {0x05, 0x01, 0x00}, output);

    newHandler().handle(socket);

    byte[] reply = output.toByteArray();
    assertTrue(reply.length >= 2, "Server must send an auth reply");
    assertEquals(0x05, reply[0]);
    assertEquals(0x00, reply[1], "Server must select NO_AUTH (0x00)");
  }
}
