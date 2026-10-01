package br.com.nicomaia.server;

import br.com.nicomaia.server.config.LogConfig;
import br.com.nicomaia.server.config.ServerConfig;
import br.com.nicomaia.server.connection.ConnectionHandler;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.tui.Dashboard;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

  private static final Logger logger = Logger.getLogger(Main.class.getName());

  static final String NO_AUTH_WARNING =
      """
      ************************************************************************
      * SECURITY WARNING: authentication is DISABLED (--no-auth).            *
      * Anyone who can reach this port can use the proxy to open connections *
      * on your behalf. Only run like this in strictly controlled            *
      * environments (local development, isolated test networks) and never   *
      * on a network reachable by untrusted hosts.                           *
      ************************************************************************""";

  static final String NO_AUTH_DASHBOARD_WARNING =
      "! AUTHENTICATION DISABLED (--no-auth) - controlled environments only";

  public static void main(String[] args) {
    boolean tuiEnabled = Arrays.stream(args).noneMatch("--no-tui"::equals);

    String[] configArgs =
        Arrays.stream(args).filter(arg -> !"--no-tui".equals(arg)).toArray(String[]::new);

    var metrics = Metrics.instance();

    UpdateChecker.checkAsync();

    ServerConfig config;
    try {
      config = ServerConfig.fromArgs(configArgs, metrics);
    } catch (IllegalArgumentException | IllegalStateException e) {
      System.err.println("Fatal: " + e.getMessage());
      System.exit(1);
      return;
    }

    if (tuiEnabled) {
      LogConfig.configureFileLogging();
    }

    // Goes to stderr through the console handler, or to the log file when the TUI is enabled
    // (the dashboard clears the screen, so it repeats the warning as a banner instead).
    startupWarning(config)
        .ifPresent(warning -> logger.logp(Level.WARNING, Main.class.getName(), "main", warning));

    if (tuiEnabled) {
      var dashboard = new Dashboard(config.port(), loadVersion(), metrics);
      if (!config.authenticationRequired()) {
        dashboard.withWarning(NO_AUTH_DASHBOARD_WARNING);
      }
      dashboard.start();
    }

    new ConnectionHandler(config, metrics).start();
  }

  static Optional<String> startupWarning(ServerConfig config) {
    return config.authenticationRequired() ? Optional.empty() : Optional.of(NO_AUTH_WARNING);
  }

  private static String loadVersion() {
    try (var stream = Main.class.getClassLoader().getResourceAsStream("version.properties")) {
      if (stream == null) return "0.0.0";
      var props = new java.util.Properties();
      props.load(stream);
      return props.getProperty("version", "0.0.0").replace("-SNAPSHOT", "");
    } catch (Exception e) {
      return "0.0.0";
    }
  }
}
