package br.com.nicomaia.server.protocol;

import br.com.nicomaia.server.auth.Socks5Credentials;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs the SOCKS5 method negotiation followed by the RFC 1929 username/password
 * sub-negotiation. Only clients offering the {@code USERNAME} method are accepted; every other
 * negotiation (including plain {@code NO_AUTH}) is rejected with {@code NO_ACCEPTABLE_METHODS}.
 *
 * <p>Operates purely on {@link InputStream}/{@link OutputStream} (no {@link java.net.Socket}
 * dependency) so it can be exercised with in-memory streams in tests.
 */
public class Socks5Authenticator {

  private static final Logger logger = Logger.getLogger(Socks5Authenticator.class.getName());

  private static final byte SOCKS_VERSION = 0x05;

  private final Socks5Credentials credentials;

  public Socks5Authenticator(Socks5Credentials credentials) {
    this.credentials = credentials;
  }

  /** @return {@code true} if the client authenticated successfully. */
  public boolean authenticate(InputStream in, OutputStream out) throws IOException {
    byte[] header = SocketReader.readFully(in, 2);
    byte socksVersion = header[0];
    int methodCount = header[1] & 0xFF;

    if (socksVersion != SOCKS_VERSION) {
      logger.info("Rejecting connection with unsupported SOCKS version: " + socksVersion);
      return false;
    }

    byte[] methodBytes = SocketReader.readFully(in, methodCount);
    Set<SupportedAuthType> offeredMethods = SupportedAuthType.valueOf(methodBytes);

    var authRequest = new AuthRequest(socksVersion, header[1], offeredMethods);
    logger.info(authRequest.toString());

    if (!offeredMethods.contains(SupportedAuthType.USERNAME)) {
      var rejection = new AuthResponse(socksVersion, SupportedAuthType.NO_ACCEPTABLE_METHODS);
      logger.warning("Client did not offer username/password authentication; rejecting");
      out.write(rejection.toBytes());
      out.flush();
      return false;
    }

    var authResponse = new AuthResponse(socksVersion, SupportedAuthType.USERNAME);
    logger.info(authResponse.toString());
    out.write(authResponse.toBytes());
    out.flush();

    var credentialsRequest = SocketReader.readUsernamePassword(in);

    if (credentialsRequest.version() != UsernamePasswordResponse.VERSION) {
      logger.warning(
          "Rejecting connection with unsupported sub-negotiation version: "
              + credentialsRequest.version());
      out.write(UsernamePasswordResponse.forOutcome(false).toBytes());
      out.flush();
      return false;
    }

    boolean valid =
        credentials.matches(credentialsRequest.username(), credentialsRequest.password());

    out.write(UsernamePasswordResponse.forOutcome(valid).toBytes());
    out.flush();

    if (!valid) {
      logger.warning("Rejected connection: invalid username/password");
    }

    return valid;
  }
}
