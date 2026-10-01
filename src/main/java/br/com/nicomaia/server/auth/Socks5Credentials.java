package br.com.nicomaia.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Credentials required to authenticate SOCKS5 clients via the RFC 1929 username/password
 * sub-negotiation. Loaded once at startup from the {@code SOCKS_USERNAME} / {@code
 * SOCKS_PASSWORD} environment variables; the server refuses to start if either is missing.
 */
public final class Socks5Credentials {

  public static final String USERNAME_ENV = "SOCKS_USERNAME";
  public static final String PASSWORD_ENV = "SOCKS_PASSWORD";

  private final byte[] username;
  private final byte[] password;

  private Socks5Credentials(byte[] username, byte[] password) {
    this.username = username.clone();
    this.password = password.clone();
  }

  public static Socks5Credentials fromEnvironment() {
    return of(System.getenv(USERNAME_ENV), System.getenv(PASSWORD_ENV));
  }

  public static Socks5Credentials of(String username, String password) {
    if (username == null || username.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + USERNAME_ENV);
    }
    if (password == null || password.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + PASSWORD_ENV);
    }
    return new Socks5Credentials(
        username.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8));
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
