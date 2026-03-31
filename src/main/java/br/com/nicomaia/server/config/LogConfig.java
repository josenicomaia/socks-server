package br.com.nicomaia.server.config;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class LogConfig {

  private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
  private static final int MAX_FILE_COUNT = 3; // 3 rotating files

  private LogConfig() {}

  public static void configureFileLogging() {
    Logger rootLogger = Logger.getLogger("");

    // Remove console handlers
    for (Handler handler : rootLogger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        rootLogger.removeHandler(handler);
      }
    }

    try {
      FileHandler fileHandler =
          new FileHandler("socks-server.%g.log", MAX_FILE_SIZE, MAX_FILE_COUNT, true);
      fileHandler.setFormatter(new SimpleFormatter());
      rootLogger.addHandler(fileHandler);
    } catch (IOException e) {
      // Fallback: if file logging fails, keep console
      System.err.println("Failed to configure file logging: " + e.getMessage());
    }
  }
}
