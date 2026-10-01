package br.com.nicomaia.server.commands.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.metrics.ConnectionRecord;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.net.AddressType;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class ConnectHandlerTest {

  private static final byte DEST_MARKER = 0x42;

  @Test
  void shouldSendSocksReplyBeforeRelayingDestinationData() throws Exception {
    InetAddress loopback = InetAddress.getLoopbackAddress();

    try (ServerSocket destination = new ServerSocket(0, 50, loopback);
        ServerSocket clientListener = new ServerSocket(0, 50, loopback)) {

      // Destination server writes payload immediately on accept. If the relay were started
      // before the SOCKS reply, these bytes could reach the client first.
      Thread destThread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (Socket s = destination.accept()) {
                      s.getOutputStream()
                          .write(new byte[] {DEST_MARKER, DEST_MARKER, DEST_MARKER, DEST_MARKER});
                      s.getOutputStream().flush();
                      Thread.sleep(300); // keep the connection open briefly
                    } catch (Exception ignored) {
                      // test teardown races are fine
                    }
                  });

      Socket clientHandlerSide = new Socket(loopback, clientListener.getLocalPort());
      Socket clientTestSide = clientListener.accept();
      clientTestSide.setSoTimeout(3000);

      Command command =
          new Command(
              (byte) 0x05,
              CommandType.CONNECT,
              AddressType.IPV4,
              loopback,
              destination.getLocalPort());

      ConnectHandler handler = new ConnectHandler(Metrics.instance());
      // handle() now blocks until the relay ends, so run it off-thread.
      Thread handlerThread =
          Thread.ofVirtual().start(() -> handler.handle(clientHandlerSide, command));

      InputStream clientIn = clientTestSide.getInputStream();

      // The full reply (VER REP RSV ATYP ADDR(4) PORT(2) for an IPv4 client) must come first,
      // followed by the destination payload relayed intact.
      byte[] reply = clientIn.readNBytes(10);
      assertEquals(10, reply.length, "Client must receive the full SOCKS reply");
      assertEquals(0x05, reply[0], "Reply must start with the SOCKS version");
      assertEquals(0x00, reply[1], "Reply must report success");

      assertArrayEquals(
          new byte[] {DEST_MARKER, DEST_MARKER, DEST_MARKER, DEST_MARKER},
          clientIn.readNBytes(4),
          "Destination data must be relayed after the SOCKS reply");

      // Teardown: close both ends so the relay + handler finish.
      clientTestSide.close();
      clientHandlerSide.close();
      handlerThread.join(3000);
      destThread.join(3000);
    }
  }

  @Test
  void shouldRecordSuccessfulConnectionWhileRelayIsActive() throws Exception {
    InetAddress loopback = InetAddress.getLoopbackAddress();
    Metrics metrics = Metrics.instance();

    try (ServerSocket destination = new ServerSocket(0, 50, loopback);
        ServerSocket clientListener = new ServerSocket(0, 50, loopback)) {

      // Destination stays connected until the relay closes it, so handle() keeps blocking.
      Thread destThread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try (Socket s = destination.accept()) {
                      s.getInputStream().readAllBytes();
                    } catch (IOException ignored) {
                      // test teardown races are fine
                    }
                  });

      Socket clientHandlerSide = new Socket(loopback, clientListener.getLocalPort());
      Socket clientTestSide = clientListener.accept();
      clientTestSide.setSoTimeout(3000);

      Command command =
          new Command(
              (byte) 0x05,
              CommandType.CONNECT,
              AddressType.IPV4,
              loopback,
              destination.getLocalPort());

      Thread handlerThread =
          Thread.ofVirtual()
              .start(() -> new ConnectHandler(metrics).handle(clientHandlerSide, command));

      clientTestSide.getInputStream().readNBytes(10); // SOCKS reply for an IPv4 client

      String expectedDestination = loopback.getHostName() + ":" + destination.getLocalPort();
      boolean recorded = false;
      long deadline = System.currentTimeMillis() + 3000;
      while (!recorded && System.currentTimeMillis() < deadline) {
        recorded =
            metrics.recentConnections().stream()
                .anyMatch(
                    r ->
                        r.destination().equals(expectedDestination)
                            && r.status() == ConnectionRecord.Status.OK);
        if (!recorded) {
          Thread.sleep(10);
        }
      }

      assertTrue(recorded, "OK record must be added when the relay starts, not when it ends");
      assertTrue(handlerThread.isAlive(), "The relay must still be active at this point");

      clientTestSide.close();
      clientHandlerSide.close();
      handlerThread.join(3000);
      destThread.join(3000);
    }
  }

  @Test
  void shouldCloseDestinationWhenSendingSuccessReplyFails() throws Exception {
    InetAddress loopback = InetAddress.getLoopbackAddress();

    try (ServerSocket destination = new ServerSocket(0, 50, loopback)) {
      Socket client = mock(Socket.class);
      when(client.getInetAddress()).thenReturn(loopback);
      when(client.getOutputStream()).thenThrow(new IOException("broken pipe"));

      Command command =
          new Command(
              (byte) 0x05,
              CommandType.CONNECT,
              AddressType.IPV4,
              loopback,
              destination.getLocalPort());

      new ConnectHandler(Metrics.instance()).handle(client, command);

      try (Socket accepted = destination.accept()) {
        accepted.setSoTimeout(3000);
        assertEquals(
            -1,
            accepted.getInputStream().read(),
            "Destination connection must be closed when the SOCKS reply cannot be sent");
      }
    }
  }
}
