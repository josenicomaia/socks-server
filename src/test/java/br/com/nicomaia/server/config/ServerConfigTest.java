package br.com.nicomaia.server.config;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.auth.Socks5Credentials;
import br.com.nicomaia.server.metrics.Metrics;
import org.junit.jupiter.api.Test;

class ServerConfigTest {

  @Test
  void shouldPropagateExceptionWhenCredentialsAreMissing() {
    // This is the contract the whole username/password auth feature relies on: the server must
    // refuse to start if SOCKS_USERNAME/SOCKS_PASSWORD aren't configured. Socks5Credentials.of
    // throws IllegalStateException for a missing value; fromArgs must let it propagate, not
    // swallow it and fall back to some default.
    assertThrows(
        IllegalStateException.class,
        () ->
            ServerConfig.fromArgs(
                new String[0], Metrics.instance(), () -> Socks5Credentials.of(null, null)));
  }

  @Test
  void shouldPropagateExceptionWhenOnlyPasswordIsMissing() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ServerConfig.fromArgs(
                new String[0], Metrics.instance(), () -> Socks5Credentials.of("alice", null)));
  }

  @Test
  void shouldBuildConfigWithSuppliedCredentialsAndDefaultPort() {
    var credentials = Socks5Credentials.of("alice", "s3cret");

    ServerConfig config =
        ServerConfig.fromArgs(new String[0], Metrics.instance(), () -> credentials);

    assertEquals(5353, config.port());
    assertSame(credentials, config.credentials());
  }

  @Test
  void shouldUsePortFromArgsWhenProvided() {
    var credentials = Socks5Credentials.of("alice", "s3cret");

    ServerConfig config =
        ServerConfig.fromArgs(new String[] {"1080"}, Metrics.instance(), () -> credentials);

    assertEquals(1080, config.port());
  }
}
