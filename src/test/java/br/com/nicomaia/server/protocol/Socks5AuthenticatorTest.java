package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.auth.Socks5Credentials;
import br.com.nicomaia.server.protocol.Socks5Authenticator.Outcome;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Socks5Authenticator} directly against in-memory streams — no real socket
 * needed, since the class depends only on {@link java.io.InputStream}/{@link
 * java.io.OutputStream}.
 */
class Socks5AuthenticatorTest {

  private static final Socks5Credentials CREDENTIALS = Socks5Credentials.of("alice", "s3cret");
  private final Socks5Authenticator authenticator =
      Socks5Authenticator.requiring(CREDENTIALS, Duration.ZERO);

  @Test
  void shouldRejectUnsupportedSocksVersionWithoutReplying() throws IOException {
    // SOCKS4 hello: version 0x04, 1 method, USERNAME
    var out = new ByteArrayOutputStream();
    var in = new ByteArrayInputStream(new byte[] {0x04, 0x01, 0x02});

    Outcome result = authenticator.authenticate(in, out);

    assertEquals(Outcome.UNSUPPORTED_VERSION, result);
    assertEquals(0, out.size(), "Server must not reply to a non-SOCKS5 hello");
  }

  @Test
  void shouldReadMethodCountAboveSignedByteRange() throws IOException {
    // NMETHODS = 0x80 (128) has the high bit set; read as a signed byte it would be negative.
    byte[] methods = new byte[0x80];
    for (int i = 0; i < methods.length; i++) {
      methods[i] = (byte) (0x80 + i); // private/unknown methods
    }
    methods[methods.length - 1] = 0x02; // USERNAME offered last
    var out = new ByteArrayOutputStream();
    var in = negotiationAndCredentials(methods, "alice", "s3cret");

    Outcome result = authenticator.authenticate(in, out);

    assertEquals(Outcome.AUTHENTICATED, result);
    assertArrayEquals(new byte[] {0x05, 0x02, 0x01, 0x00}, out.toByteArray());
  }

  @Test
  void shouldRejectWhenClientOffersNoMethods() throws IOException {
    var out = new ByteArrayOutputStream();
    var in = new ByteArrayInputStream(new byte[] {0x05, 0x00}); // VER, NMETHODS=0

    Outcome result = authenticator.authenticate(in, out);

    assertEquals(Outcome.NO_ACCEPTABLE_METHOD, result);
    assertArrayEquals(new byte[] {0x05, (byte) 0xFF}, out.toByteArray());
  }

  @Test
  void shouldRejectWhenOnlyNoAuthIsOffered() throws IOException {
    var out = new ByteArrayOutputStream();
    var in = new ByteArrayInputStream(new byte[] {0x05, 0x01, 0x00});

    Outcome result = authenticator.authenticate(in, out);

    assertEquals(Outcome.NO_ACCEPTABLE_METHOD, result);
    assertArrayEquals(new byte[] {0x05, (byte) 0xFF}, out.toByteArray());
  }

  @Test
  void shouldAcceptUsernameMethodWhenOfferedAlongsideNoAuth() throws IOException {
    var out = new ByteArrayOutputStream();
    var in = negotiationAndCredentials(new byte[] {0x00, 0x02}, "alice", "s3cret");

    Outcome result = authenticator.authenticate(in, out);

    assertEquals(Outcome.AUTHENTICATED, result);
    assertArrayEquals(new byte[] {0x05, 0x02, 0x01, 0x00}, out.toByteArray());
  }

  @Test
  void shouldRejectInvalidCredentials() throws IOException {
    var out = new ByteArrayOutputStream();
    var in = negotiationAndCredentials(new byte[] {0x02}, "alice", "wrong-password");

    Outcome result = authenticator.authenticate(in, out);

    assertEquals(Outcome.INVALID_CREDENTIALS, result);
    assertArrayEquals(new byte[] {0x05, 0x02, 0x01, 0x01}, out.toByteArray());
  }

  @Test
  void shouldDelayReplyToInvalidCredentials() throws IOException {
    var delayed = Socks5Authenticator.requiring(CREDENTIALS, Duration.ofMillis(200));
    var out = new ByteArrayOutputStream();
    var in = negotiationAndCredentials(new byte[] {0x02}, "alice", "wrong-password");

    long start = System.nanoTime();
    Outcome result = delayed.authenticate(in, out);
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

    assertEquals(Outcome.INVALID_CREDENTIALS, result);
    assertTrue(elapsedMillis >= 200, "Failure reply came after only " + elapsedMillis + "ms");
  }

  @Test
  void shouldUseOneSecondFailureDelayByDefault() {
    assertEquals(Duration.ofSeconds(1), Socks5Authenticator.DEFAULT_FAILURE_DELAY);
  }

  @Test
  void shouldRejectUnsupportedSubNegotiationVersionEvenWithValidCredentials()
      throws IOException {
    var out = new ByteArrayOutputStream();
    // Client sends an invalid sub-negotiation VER byte (0x07 instead of 0x01).
    byte[] username = "alice".getBytes(StandardCharsets.UTF_8);
    byte[] password = "s3cret".getBytes(StandardCharsets.UTF_8);
    var payload = new ByteArrayOutputStream();
    payload.write(new byte[] {0x05, 0x01, 0x02});
    payload.write(0x07); // bogus sub-negotiation version
    payload.write(username.length);
    payload.write(username);
    payload.write(password.length);
    payload.write(password);
    var in = new ByteArrayInputStream(payload.toByteArray());

    Outcome result = authenticator.authenticate(in, out);

    assertEquals(Outcome.UNSUPPORTED_SUBNEGOTIATION_VERSION, result);
    // Bytes 2-3 are the sub-negotiation response: VER=0x01 (RFC 1929, never the client's 0x07)
    // and a failure status.
    assertArrayEquals(new byte[] {0x05, 0x02, 0x01, 0x01}, out.toByteArray());
  }

  @Test
  void shouldPropagateEofWhenClientDisconnectsMidCredentials() {
    var payload = new ByteArrayOutputStream();
    payload.writeBytes(new byte[] {0x05, 0x01, 0x02});
    payload.writeBytes(new byte[] {0x01, 0x05, 'a', 'l'}); // ULEN=5 but only 2 bytes follow
    var in = new ByteArrayInputStream(payload.toByteArray());

    assertThrows(EOFException.class, () -> authenticator.authenticate(in, new ByteArrayOutputStream()));
  }

  @Test
  void shouldRequireAuthenticationWhenBuiltWithCredentials() {
    assertTrue(Socks5Authenticator.requiring(CREDENTIALS).isAuthenticationRequired());
  }

  // --- --no-auth mode ---

  @Test
  void shouldAcceptNoAuthClientWhenAuthenticationIsDisabled() throws IOException {
    var noAuth = Socks5Authenticator.withoutAuthentication();
    var out = new ByteArrayOutputStream();
    var in = new ByteArrayInputStream(new byte[] {0x05, 0x01, 0x00});

    Outcome result = noAuth.authenticate(in, out);

    assertFalse(noAuth.isAuthenticationRequired());
    assertEquals(Outcome.AUTHENTICATED, result);
    assertArrayEquals(new byte[] {0x05, 0x00}, out.toByteArray());
  }

  @Test
  void shouldSelectNoAuthWhenClientOffersBothMethodsAndAuthenticationIsDisabled()
      throws IOException {
    var out = new ByteArrayOutputStream();
    var in = new ByteArrayInputStream(new byte[] {0x05, 0x02, 0x00, 0x02});

    Outcome result = Socks5Authenticator.withoutAuthentication().authenticate(in, out);

    assertEquals(Outcome.AUTHENTICATED, result);
    // Only the method-selection reply: no credential sub-negotiation takes place.
    assertArrayEquals(new byte[] {0x05, 0x00}, out.toByteArray());
  }

  @Test
  void shouldRejectClientNotOfferingNoAuthWhenAuthenticationIsDisabled() throws IOException {
    var out = new ByteArrayOutputStream();
    var in = new ByteArrayInputStream(new byte[] {0x05, 0x01, 0x02}); // USERNAME only

    Outcome result = Socks5Authenticator.withoutAuthentication().authenticate(in, out);

    assertEquals(Outcome.NO_ACCEPTABLE_METHOD, result);
    assertArrayEquals(new byte[] {0x05, (byte) 0xFF}, out.toByteArray());
  }

  private static ByteArrayInputStream negotiationAndCredentials(
      byte[] methods, String username, String password) throws IOException {
    byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
    byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);

    var payload = new ByteArrayOutputStream();
    payload.write(0x05);
    payload.write(methods.length);
    payload.write(methods);
    payload.write(0x01); // sub-negotiation VER
    payload.write(usernameBytes.length);
    payload.write(usernameBytes);
    payload.write(passwordBytes.length);
    payload.write(passwordBytes);

    return new ByteArrayInputStream(payload.toByteArray());
  }
}
