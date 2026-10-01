package br.com.nicomaia.server.config;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.metrics.Metrics;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ServerConfigTest {

  private static final Function<String, String> CREDENTIALS_ENV =
      Map.of("SOCKS_USERNAME", "alice", "SOCKS_PASSWORD", "s3cret")::get;

  private static final Function<String, String> EMPTY_ENV = name -> null;

  @Test
  void shouldRefuseToStartWhenCredentialsAreMissing() {
    // This is the contract the whole username/password auth feature relies on: the server must
    // refuse to start if SOCKS_USERNAME/SOCKS_PASSWORD aren't configured.
    var exception = assertThrows(IllegalStateException.class, () -> fromArgs(EMPTY_ENV));

    assertTrue(exception.getMessage().contains("SOCKS_USERNAME"));
  }

  @Test
  void shouldRefuseToStartWhenOnlyPasswordIsMissing() {
    var exception =
        assertThrows(
            IllegalStateException.class, () -> fromArgs(Map.of("SOCKS_USERNAME", "alice")::get));

    assertTrue(exception.getMessage().contains("SOCKS_PASSWORD"));
  }

  @Test
  void shouldRequireAuthenticationWithDefaultPortWhenCredentialsAreSet() {
    ServerConfig config = fromArgs(CREDENTIALS_ENV);

    assertEquals(5353, config.port());
    assertTrue(config.authenticationRequired());
  }

  @Test
  void shouldUsePortFromArgsWhenProvided() {
    assertEquals(1080, fromArgs(CREDENTIALS_ENV, "1080").port());
  }

  @Test
  void shouldDisableAuthenticationWithNoAuthFlagWhenNoCredentialsAreSet() {
    ServerConfig config = fromArgs(EMPTY_ENV, ServerConfig.NO_AUTH_FLAG);

    assertFalse(config.authenticationRequired());
    assertEquals(5353, config.port());
  }

  @Test
  void shouldAcceptNoAuthFlagInAnyPositionAlongsidePort() {
    ServerConfig before = fromArgs(EMPTY_ENV, ServerConfig.NO_AUTH_FLAG, "1080");
    ServerConfig after = fromArgs(EMPTY_ENV, "1080", ServerConfig.NO_AUTH_FLAG);

    assertEquals(1080, before.port());
    assertFalse(before.authenticationRequired());
    assertEquals(1080, after.port());
    assertFalse(after.authenticationRequired());
  }

  @Test
  void shouldRefuseNoAuthFlagWhenCredentialsAreAlsoSet() {
    // Contradictory configuration must fail closed instead of silently running an open proxy.
    var exception =
        assertThrows(
            IllegalStateException.class,
            () -> fromArgs(CREDENTIALS_ENV, ServerConfig.NO_AUTH_FLAG));

    assertTrue(exception.getMessage().contains(ServerConfig.NO_AUTH_FLAG));
  }

  @Test
  void shouldRefuseNoAuthFlagWhenOnlyOneCredentialIsSet() {
    assertThrows(
        IllegalStateException.class,
        () -> fromArgs(Map.of("SOCKS_PASSWORD", "s3cret")::get, ServerConfig.NO_AUTH_FLAG));
  }

  @Test
  void shouldTreatBlankCredentialVariablesAsUnsetForNoAuth() {
    Function<String, String> blankEnv = Map.of("SOCKS_USERNAME", " ", "SOCKS_PASSWORD", "")::get;

    assertFalse(fromArgs(blankEnv, ServerConfig.NO_AUTH_FLAG).authenticationRequired());
  }

  @Test
  void shouldRejectUnknownOption() {
    // A typo such as --noauth must never be mistaken for a port or silently ignored.
    var exception =
        assertThrows(IllegalArgumentException.class, () -> fromArgs(EMPTY_ENV, "--noauth"));

    assertTrue(exception.getMessage().contains("--noauth"));
  }

  @Test
  void shouldRejectNonNumericPort() {
    assertThrows(IllegalArgumentException.class, () -> fromArgs(CREDENTIALS_ENV, "http"));
  }

  @Test
  void shouldRejectPortOutOfRange() {
    assertThrows(IllegalArgumentException.class, () -> fromArgs(CREDENTIALS_ENV, "99999"));
    assertThrows(IllegalArgumentException.class, () -> fromArgs(CREDENTIALS_ENV, "0"));
  }

  @Test
  void shouldRejectExtraPositionalArguments() {
    assertThrows(IllegalArgumentException.class, () -> fromArgs(CREDENTIALS_ENV, "1080", "1081"));
  }

  private static ServerConfig fromArgs(Function<String, String> env, String... args) {
    return ServerConfig.fromArgs(args, Metrics.instance(), env);
  }
}
