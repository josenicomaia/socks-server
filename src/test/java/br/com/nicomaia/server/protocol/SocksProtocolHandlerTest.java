package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.auth.Socks5Credentials;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import br.com.nicomaia.server.net.AddressResolver;
import br.com.nicomaia.server.net.AddressType;
import br.com.nicomaia.server.net.resolvers.InetResolver;
import br.com.nicomaia.server.net.resolvers.IpInetResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Covers the end-to-end wiring between authentication, throttling, the handshake deadline and
 * command dispatch over a real socket — what {@link Socks5AuthenticatorTest} (plain streams)
 * can't prove. Auth edge cases (rejected methods, wrong credentials, protocol version handling)
 * live there instead, since they don't need a socket at all.
 */
class SocksProtocolHandlerTest {

  private static final Socks5Authenticator AUTHENTICATOR =
      Socks5Authenticator.requiring(Socks5Credentials.of("alice", "s3cret"));

  private static final byte[] CONNECT_LOOPBACK_80 = {0x05, 0x01, 0x00, 0x01, 127, 0, 0, 1, 0, 80};

  @Test
  void shouldNotDispatchCommandWhenAuthenticationFails() throws Exception {
    try (var harness = Harness.builder().start();
        Socket client = harness.connectClient()) {
      OutputStream out = client.getOutputStream();

      out.write(new byte[] {0x05, 0x01, 0x00}); // VER, NMETHODS, NO_AUTH only
      out.flush();

      assertArrayEquals(new byte[] {0x05, (byte) 0xFF}, client.getInputStream().readNBytes(2));
      assertEquals(-1, client.getInputStream().read());
      assertFalse(harness.awaitCommandDispatched(200, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void shouldCloseConnectionAfterInvalidCredentials() throws Exception {
    try (var harness = Harness.builder().start();
        Socket client = harness.connectClient()) {
      assertEquals(0x01, login(client, "alice", "wrong-password"));

      // RFC 1929: on failure the server MUST close the connection.
      assertEquals(-1, client.getInputStream().read());
      assertFalse(harness.awaitCommandDispatched(200, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void shouldAuthenticateAndDispatchCommand() throws Exception {
    try (var harness = Harness.builder().start();
        Socket client = harness.connectClient()) {
      assertEquals(0x00, login(client, "alice", "s3cret"));

      client.getOutputStream().write(CONNECT_LOOPBACK_80);
      client.getOutputStream().flush();

      assertTrue(harness.awaitCommandDispatched(2, TimeUnit.SECONDS));
      // The relay may sit idle for a long time, so the handshake timeout must be lifted.
      assertEquals(0, harness.soTimeoutSeenByCommandHandler());
    }
  }

  @Test
  void shouldRejectCommandWithUnsupportedVersion() throws Exception {
    try (var harness = Harness.builder().start();
        Socket client = harness.connectClient()) {
      assertEquals(0x00, login(client, "alice", "s3cret"));

      byte[] socks4Request = CONNECT_LOOPBACK_80.clone();
      socks4Request[0] = 0x04;
      client.getOutputStream().write(socks4Request);
      client.getOutputStream().flush();

      assertEquals(-1, client.getInputStream().read());
      assertFalse(harness.awaitCommandDispatched(200, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void shouldRejectCommandWithNonZeroReservedByte() throws Exception {
    try (var harness = Harness.builder().start();
        Socket client = harness.connectClient()) {
      assertEquals(0x00, login(client, "alice", "s3cret"));

      byte[] request = CONNECT_LOOPBACK_80.clone();
      request[2] = 0x7F;
      client.getOutputStream().write(request);
      client.getOutputStream().flush();

      assertEquals(-1, client.getInputStream().read());
      assertFalse(harness.awaitCommandDispatched(200, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void shouldDispatchCommandWithoutCredentialsWhenAuthenticationIsDisabled() throws Exception {
    try (var harness =
            Harness.builder().authenticator(Socks5Authenticator.withoutAuthentication()).start();
        Socket client = harness.connectClient()) {
      OutputStream out = client.getOutputStream();

      out.write(new byte[] {0x05, 0x01, 0x00}); // NO_AUTH only
      out.flush();
      assertArrayEquals(new byte[] {0x05, 0x00}, client.getInputStream().readNBytes(2));

      out.write(CONNECT_LOOPBACK_80); // straight after method selection
      out.flush();

      assertTrue(harness.awaitCommandDispatched(2, TimeUnit.SECONDS));
    }
  }

  @Test
  void shouldCloseIdleConnectionWhenHandshakeTimesOut() throws Exception {
    try (var harness = Harness.builder().handshakeTimeout(Duration.ofMillis(300)).start();
        Socket client = harness.connectClient()) {
      client.setSoTimeout(5_000); // fail the test instead of hanging if the server never closes

      client.getOutputStream().write(0x05); // partial greeting, then go silent
      client.getOutputStream().flush();

      // Returns (instead of hitting the 5s client timeout) only if the server closes.
      assertEquals(0, readUntilClosed(client.getInputStream()).length);
      assertFalse(harness.awaitCommandDispatched(100, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void shouldEnforceHandshakeDeadlineAcrossReadsWhenClientDripsBytes() throws Exception {
    // Each byte arrives well within the per-read budget, but the whole handshake (26 bytes at
    // 100ms each ≈ 2.6s) can't finish within the 500ms deadline.
    try (var harness = Harness.builder().handshakeTimeout(Duration.ofMillis(500)).start();
        Socket client = harness.connectClient()) {
      client.setSoTimeout(5_000);
      byte[] handshake = fullHandshake("alice", "s3cret");

      Thread dripper =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      for (byte b : handshake) {
                        client.getOutputStream().write(b);
                        client.getOutputStream().flush();
                        Thread.sleep(100);
                      }
                    } catch (IOException | InterruptedException expected) {
                      // The server closed the connection mid-drip.
                    }
                  });

      long start = System.nanoTime();
      readUntilClosed(client.getInputStream()); // returns only once the server closes
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      dripper.join(5_000);

      assertTrue(elapsedMillis < 2_000, "Server took " + elapsedMillis + "ms to close");
      assertFalse(harness.awaitCommandDispatched(100, TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void shouldRefuseAddressAfterRepeatedFailedLogins() throws Exception {
    var throttle = new HandshakeThrottle(8, 2, Duration.ofMinutes(1), Clock.systemUTC());
    try (var harness = Harness.builder().throttle(throttle).start()) {
      for (int attempt = 0; attempt < 2; attempt++) {
        try (Socket client = harness.connectClient()) {
          assertEquals(0x01, login(client, "alice", "guess-" + attempt));
        }
      }

      try (Socket blocked = harness.connectClient()) {
        blocked.setSoTimeout(5_000);
        blocked.getOutputStream().write(new byte[] {0x05, 0x01, 0x02});
        blocked.getOutputStream().flush();

        // Closed without even a method-selection reply.
        byte[] received = readUntilClosed(blocked.getInputStream());
        assertEquals(0, received.length);
      }
    }
  }

  /** Performs greeting + RFC 1929 login and returns the sub-negotiation status byte. */
  private static int login(Socket client, String username, String password) throws IOException {
    OutputStream out = client.getOutputStream();
    InputStream in = client.getInputStream();

    out.write(new byte[] {0x05, 0x01, 0x02});
    out.flush();
    assertArrayEquals(new byte[] {0x05, 0x02}, in.readNBytes(2));

    out.write(usernamePassword(username, password));
    out.flush();
    byte[] reply = in.readNBytes(2);
    assertEquals(0x01, reply[0]);
    return reply[1];
  }

  private static byte[] fullHandshake(String username, String password) {
    var bytes = new ByteArrayOutputStream();
    bytes.writeBytes(new byte[] {0x05, 0x01, 0x02});
    bytes.writeBytes(usernamePassword(username, password));
    bytes.writeBytes(CONNECT_LOOPBACK_80);
    return bytes.toByteArray();
  }

  private static byte[] usernamePassword(String username, String password) {
    byte[] u = username.getBytes(StandardCharsets.UTF_8);
    byte[] p = password.getBytes(StandardCharsets.UTF_8);
    var bytes = new ByteArrayOutputStream();
    bytes.write(0x01);
    bytes.write(u.length);
    bytes.writeBytes(u);
    bytes.write(p.length);
    bytes.writeBytes(p);
    return bytes.toByteArray();
  }

  /**
   * Reads until the server closes the connection (EOF or reset) and returns what it sent. A
   * server that never closes makes this throw once the client socket timeout expires.
   */
  private static byte[] readUntilClosed(InputStream in) throws IOException {
    var received = new ByteArrayOutputStream();
    try {
      int b;
      while ((b = in.read()) != -1) {
        received.write(b);
      }
    } catch (SocketException reset) {
      // Treated as closed.
    }
    return received.toByteArray();
  }

  /** Spins up a real loopback server socket wired to a {@link SocksProtocolHandler}. */
  private static final class Harness implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final CountDownLatch commandLatch = new CountDownLatch(1);
    private final AtomicInteger commandHandlerSoTimeout = new AtomicInteger(-1);

    private Harness(
        Socks5Authenticator authenticator, HandshakeThrottle throttle, Duration handshakeTimeout)
        throws IOException {
      this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());

      Map<AddressType, InetResolver> resolvers = Map.of(AddressType.IPV4, new IpInetResolver());
      var addressResolver = new AddressResolver(resolvers);
      var handlers = new HandlersHolder();
      handlers.register(
          CommandType.CONNECT,
          (clientSocket, command) -> {
            try {
              commandHandlerSoTimeout.set(clientSocket.getSoTimeout());
            } catch (SocketException e) {
              throw new IllegalStateException(e);
            }
            commandLatch.countDown();
          });

      var protocolHandler =
          new SocksProtocolHandler(
              addressResolver,
              handlers,
              authenticator,
              throttle,
              handshakeTimeout,
              Clock.systemUTC());

      Thread acceptLoop =
          new Thread(
              () -> {
                try {
                  while (true) {
                    Socket accepted = serverSocket.accept();
                    Thread.ofVirtual().start(() -> protocolHandler.handle(accepted));
                  }
                } catch (IOException ignored) {
                  // Server socket closed during test teardown.
                }
              });
      acceptLoop.setDaemon(true);
      acceptLoop.start();
    }

    static Builder builder() {
      return new Builder();
    }

    Socket connectClient() throws IOException {
      return new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
    }

    boolean awaitCommandDispatched(long timeout, TimeUnit unit) throws InterruptedException {
      return commandLatch.await(timeout, unit);
    }

    int soTimeoutSeenByCommandHandler() {
      return commandHandlerSoTimeout.get();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
    }

    private static final class Builder {
      private Socks5Authenticator authenticator = AUTHENTICATOR;
      private HandshakeThrottle throttle = new HandshakeThrottle();
      private Duration handshakeTimeout = SocksProtocolHandler.DEFAULT_HANDSHAKE_TIMEOUT;

      Builder authenticator(Socks5Authenticator authenticator) {
        this.authenticator = authenticator;
        return this;
      }

      Builder throttle(HandshakeThrottle throttle) {
        this.throttle = throttle;
        return this;
      }

      Builder handshakeTimeout(Duration handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
        return this;
      }

      Harness start() throws IOException {
        return new Harness(authenticator, throttle, handshakeTimeout);
      }
    }
  }
}
