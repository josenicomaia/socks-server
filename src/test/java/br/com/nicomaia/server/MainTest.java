package br.com.nicomaia.server;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.auth.Socks5Credentials;
import br.com.nicomaia.server.config.ServerConfig;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.protocol.Socks5Authenticator;
import br.com.nicomaia.server.tui.Dashboard;
import java.io.OutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class MainTest {

  @Test
  void shouldWarnAboutControlledEnvironmentsWhenAuthenticationIsDisabled() {
    var config = new ServerConfig(5353, null, null, Socks5Authenticator.withoutAuthentication());

    String warning = Main.startupWarning(config).orElseThrow();

    assertTrue(warning.contains("--no-auth"));
    assertTrue(warning.contains("strictly controlled"));
  }

  @Test
  void shouldNotWarnWhenAuthenticationIsRequired() {
    var config =
        new ServerConfig(
            5353, null, null, Socks5Authenticator.requiring(Socks5Credentials.of("a", "b")));

    assertTrue(Main.startupWarning(config).isEmpty());
  }

  @Test
  void shouldFitDashboardWarningInsideTheFrame() {
    var dashboard =
        new Dashboard(
            5353, "1.0.0", Metrics.instance(), new PrintStream(OutputStream.nullOutputStream()));

    assertDoesNotThrow(() -> dashboard.withWarning(Main.NO_AUTH_DASHBOARD_WARNING));
  }
}
