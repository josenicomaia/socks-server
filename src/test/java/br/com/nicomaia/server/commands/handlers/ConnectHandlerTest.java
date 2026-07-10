package br.com.nicomaia.server.commands.handlers;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.net.AddressType;
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
      int firstByte = clientIn.read();

      assertEquals(
          0x05,
          firstByte,
          "First byte to the client must be the SOCKS reply version, not destination data");

      // Teardown: close both ends so the relay + handler finish.
      clientTestSide.close();
      clientHandlerSide.close();
      handlerThread.join(3000);
      destThread.join(3000);
    }
  }
}
