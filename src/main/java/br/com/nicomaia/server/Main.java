package br.com.nicomaia.server;

import br.com.nicomaia.server.config.ServerConfig;
import br.com.nicomaia.server.connection.ConnectionHandler;

public class Main {
  public static void main(String[] args) {
    UpdateChecker.checkAsync();
    var config = ServerConfig.fromArgs(args);
    new ConnectionHandler(config).start();
  }
}
