package br.com.nicomaia.server;

import br.com.nicomaia.server.config.LogConfig;
import br.com.nicomaia.server.config.ServerConfig;
import br.com.nicomaia.server.connection.ConnectionHandler;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.tui.Dashboard;
import java.util.Arrays;

public class Main {
  public static void main(String[] args) {
    boolean tuiEnabled = Arrays.stream(args).noneMatch("--no-tui"::equals);

    String[] configArgs =
        Arrays.stream(args).filter(arg -> !"--no-tui".equals(arg)).toArray(String[]::new);

    var metrics = Metrics.instance();

    UpdateChecker.checkAsync();
    var config = ServerConfig.fromArgs(configArgs, metrics);

    if (tuiEnabled) {
      LogConfig.configureFileLogging();
      new Dashboard(config.port(), loadVersion(), metrics).start();
    }

    new ConnectionHandler(config, metrics).start();
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
