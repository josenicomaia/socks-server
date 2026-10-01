package br.com.nicomaia.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.function.Function;

/**
 * Credentials required to authenticate SOCKS5 clients via the RFC 1929 username/password
 * sub-negotiation. Loaded once at startup from the {@code SOCKS_USERNAME} / {@code
 * SOCKS_PASSWORD} environment variables; the server refuses to start if either is missing or
 * invalid.
 */
public final class Socks5Credentials {

  public static final String USERNAME_ENV = "SOCKS_USERNAME";
  public static final String PASSWORD_ENV = "SOCKS_PASSWORD";

  /** RFC 1929 encodes ULEN/PLEN in a single byte, so longer values can never authenticate. */
  public static final int MAX_LENGTH_BYTES = 255;

  private final byte[] username;
  private final byte[] password;

  private Socks5Credentials(byte[] username, byte[] password) {
    this.username = username;
    this.password = password;
  }

  public static Socks5Credentials fromEnvironment() {
    return fromEnvironment(System::getenv);
  }

  /** @param env environment lookup, e.g. {@code System::getenv} */
  public static Socks5Credentials fromEnvironment(Function<String, String> env) {
    return of(requireEnv(env, USERNAME_ENV), requireEnv(env, PASSWORD_ENV));
  }

  /** @return whether either credential variable is set to a non-blank value */
  public static boolean isConfigured(Function<String, String> env) {
    return !isBlank(env.apply(USERNAME_ENV)) || !isBlank(env.apply(PASSWORD_ENV));
  }

  public static Socks5Credentials of(String username, String password) {
    return new Socks5Credentials(encode("Username", username), encode("Password", password));
  }

  private static String requireEnv(Function<String, String> env, String name) {
    String value = env.apply(name);
    if (isBlank(value)) {
      throw new IllegalStateException("Missing required environment variable: " + name);
    }
    return value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static byte[] encode(String field, String value) {
    if (isBlank(value)) {
      throw new IllegalStateException(field + " must not be blank");
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_LENGTH_BYTES) {
      throw new IllegalStateException(
          field
              + " must be at most "
              + MAX_LENGTH_BYTES
              + " bytes in UTF-8 (RFC 1929), got "
              + bytes.length);
    }
    return bytes;
  }

  /**
   * Compares the supplied credentials against the configured ones. Both fields are always
   * compared (constant-time via {@link MessageDigest#isEqual}) so mismatches don't leak timing
   * information about which field failed.
   */
  public boolean matches(byte[] candidateUsername, byte[] candidatePassword) {
    Objects.requireNonNull(candidateUsername);
    Objects.requireNonNull(candidatePassword);

    boolean usernameMatches = MessageDigest.isEqual(username, candidateUsername);
    boolean passwordMatches = MessageDigest.isEqual(password, candidatePassword);

    return usernameMatches & passwordMatches;
  }
}
