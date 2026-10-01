package br.com.nicomaia.server.protocol;

import br.com.nicomaia.server.auth.Socks5Credentials;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Performs the SOCKS5 method negotiation and, when credentials are required, the RFC 1929
 * username/password sub-negotiation.
 *
 * <p>By default only clients offering {@code USERNAME} are accepted; every other negotiation
 * (including plain {@code NO_AUTH}) is rejected with {@code NO_ACCEPTABLE_METHODS}. The {@link
 * #withoutAuthentication()} mode inverts this and only accepts {@code NO_AUTH}; it exists for
 * strictly controlled environments and must never face an untrusted network.
 *
 * <p>Operates purely on {@link InputStream}/{@link OutputStream} (no {@link java.net.Socket}
 * dependency) so it can be exercised with in-memory streams in tests. Logging of rejections and
 * brute-force throttling are left to the caller, which knows the client's address.
 */
public class Socks5Authenticator {

  private static final Logger logger = Logger.getLogger(Socks5Authenticator.class.getName());

  static final byte SOCKS_VERSION = 0x05;

  public enum Outcome {
    ACCEPTED,
    UNSUPPORTED_VERSION,
    NO_ACCEPTABLE_METHOD,
    UNSUPPORTED_SUBNEGOTIATION_VERSION,
    INVALID_CREDENTIALS;

    public boolean isAccepted() {
      return this == ACCEPTED;
    }
  }

  /** {@code null} when authentication is disabled. */
  private final Socks5Credentials credentials;

  private Socks5Authenticator(Socks5Credentials credentials) {
    this.credentials = credentials;
  }

  public static Socks5Authenticator requiring(Socks5Credentials credentials) {
    return new Socks5Authenticator(Objects.requireNonNull(credentials));
  }

  /** Accepts {@code NO_AUTH} clients only. Use exclusively in strictly controlled environments. */
  public static Socks5Authenticator withoutAuthentication() {
    return new Socks5Authenticator(null);
  }

  public boolean isAuthenticationRequired() {
    return credentials != null;
  }

  public Outcome authenticate(InputStream in, OutputStream out) throws IOException {
    byte[] header = SocketReader.readFully(in, 2);
    if (header[0] != SOCKS_VERSION) {
      return Outcome.UNSUPPORTED_VERSION; // not SOCKS5: don't answer in a protocol it doesn't speak
    }

    Set<SupportedAuthType> offeredMethods =
        SupportedAuthType.valueOf(SocketReader.readFully(in, header[1] & 0xFF));
    logger.info(new AuthRequest(header[0], header[1], offeredMethods).toString());

    SupportedAuthType requiredMethod =
        isAuthenticationRequired() ? SupportedAuthType.USERNAME : SupportedAuthType.NO_AUTH;

    if (!offeredMethods.contains(requiredMethod)) {
      reply(out, new AuthResponse(SOCKS_VERSION, SupportedAuthType.NO_ACCEPTABLE_METHODS));
      return Outcome.NO_ACCEPTABLE_METHOD;
    }

    reply(out, new AuthResponse(SOCKS_VERSION, requiredMethod));

    return isAuthenticationRequired() ? verifyCredentials(in, out) : Outcome.ACCEPTED;
  }

  private Outcome verifyCredentials(InputStream in, OutputStream out) throws IOException {
    var request = UsernamePasswordRequest.readFrom(in);

    Outcome outcome;
    if (request.version() != UsernamePasswordResponse.VERSION) {
      outcome = Outcome.UNSUPPORTED_SUBNEGOTIATION_VERSION;
    } else if (!credentials.matches(request.username(), request.password())) {
      outcome = Outcome.INVALID_CREDENTIALS;
    } else {
      outcome = Outcome.ACCEPTED;
    }

    out.write(UsernamePasswordResponse.forOutcome(outcome.isAccepted()).toBytes());
    out.flush();
    return outcome;
  }

  private static void reply(OutputStream out, AuthResponse response) throws IOException {
    logger.info(response.toString());
    out.write(response.toBytes());
    out.flush();
  }
}
