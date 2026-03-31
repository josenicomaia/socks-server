package br.com.nicomaia.server.tui;

import br.com.nicomaia.server.metrics.ConnectionRecord;
import br.com.nicomaia.server.metrics.Metrics;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Dashboard {

  private static final int REFRESH_MS = 500;
  private static final int WIDTH = 74;
  private static final String RESET = "\033[0m";
  private static final String BOLD = "\033[1m";
  private static final String DIM = "\033[2m";
  private static final String GREEN = "\033[32m";
  private static final String RED = "\033[31m";
  private static final String CYAN = "\033[36m";
  private static final String YELLOW = "\033[33m";
  private static final String WHITE = "\033[37m";
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final int MAX_VISIBLE_CONNECTIONS = 10;

  private final Metrics metrics;
  private final int port;
  private final String version;
  private final PrintStream out;

  public Dashboard(int port, String version, Metrics metrics) {
    this(port, version, metrics, System.out);
  }

  public Dashboard(int port, String version, Metrics metrics, PrintStream out) {
    this.metrics = metrics;
    this.port = port;
    this.version = version;
    this.out = out;
  }

  public void start() {
    Thread.ofVirtual()
        .name("tui-dashboard")
        .start(
            () -> {
              clearScreen();
              while (!Thread.currentThread().isInterrupted()) {
                try {
                  render();
                  Thread.sleep(REFRESH_MS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
            });
  }

  private void clearScreen() {
    out.print("\033[2J");
    out.flush();
  }

  private void render() {
    StringBuilder sb = new StringBuilder();
    sb.append("\033[H"); // cursor home

    String uptime = formatUptime(Duration.between(metrics.startTime(), Instant.now()));
    String activeConns = String.valueOf(metrics.activeConnections());
    String totalConns = String.valueOf(metrics.totalConnections());
    String bytesUp = formatBytes(metrics.bytesUploaded());
    String bytesDown = formatBytes(metrics.bytesDownloaded());

    // Header
    sb.append(DIM).append("╔").append("═".repeat(WIDTH)).append("╗").append(RESET).append("\n");
    sb.append(DIM).append("║").append(RESET);
    sb.append(BOLD).append(CYAN).append("  socks-server").append(RESET);
    sb.append(DIM).append(" v").append(version).append(RESET);
    sb.append(padTo("port: " + port, WIDTH - 16 - version.length() - 2));
    sb.append(DIM).append("║").append(RESET).append("\n");
    sb.append(DIM).append("║").append(" ".repeat(WIDTH)).append("║").append(RESET).append("\n");

    // Stats
    sb.append(DIM).append("║").append(RESET);
    sb.append(label("  Status")).append(GREEN).append(BOLD).append("online").append(RESET);
    sb.append(label("          Uptime")).append(WHITE).append(uptime).append(RESET);
    sb.append(padRight("", WIDTH - 42 - uptime.length()));
    sb.append(DIM).append("║").append(RESET).append("\n");

    sb.append(DIM).append("║").append(RESET);
    sb.append(label("  Active")).append(YELLOW).append(BOLD).append(activeConns).append(RESET);
    sb.append(" conns");
    sb.append(label("       Total")).append(WHITE).append(totalConns).append(RESET);
    sb.append(" conns");
    int usedChars = 36 + activeConns.length() + totalConns.length();
    sb.append(padRight("", WIDTH - usedChars));
    sb.append(DIM).append("║").append(RESET).append("\n");

    sb.append(DIM).append("║").append(RESET);
    sb.append(label("  Transfer")).append(CYAN).append("↑ ").append(bytesUp).append(RESET);
    sb.append(label("       ↓ ")).append(CYAN).append(bytesDown).append(RESET);
    int transferUsed = 25 + bytesUp.length() + bytesDown.length();
    sb.append(padRight("", WIDTH - transferUsed));
    sb.append(DIM).append("║").append(RESET).append("\n");

    sb.append(DIM).append("║").append(" ".repeat(WIDTH)).append("║").append(RESET).append("\n");

    // Separator
    sb.append(DIM)
        .append("║──────────────────────────────────────────────────────────────────────────║")
        .append(RESET)
        .append("\n");
    sb.append(DIM).append("║").append(RESET);
    sb.append(BOLD).append("  Recent Connections").append(RESET);
    sb.append(padRight("", WIDTH - 20));
    sb.append(DIM).append("║").append(RESET).append("\n");
    sb.append(DIM).append("║").append(" ".repeat(WIDTH)).append("║").append(RESET).append("\n");

    // Table header
    sb.append(DIM).append("║").append(RESET);
    sb.append(DIM)
        .append("  TIME       STATUS  DESTINATION                     BYTES               ")
        .append(RESET);
    sb.append(DIM).append("║").append(RESET).append("\n");

    // Connection rows
    List<ConnectionRecord> connections = metrics.recentConnections();
    int rows = Math.min(connections.size(), MAX_VISIBLE_CONNECTIONS);

    for (int i = 0; i < rows; i++) {
      ConnectionRecord conn = connections.get(i);
      sb.append(DIM).append("║").append(RESET);
      sb.append("  ").append(conn.time().format(TIME_FMT));
      sb.append("   ");

      if (conn.status() == ConnectionRecord.Status.OK) {
        sb.append(GREEN).append("OK  ").append(RESET);
      } else {
        sb.append(RED).append("FAIL").append(RESET);
      }
      sb.append("    ");

      String dest = truncate(conn.destination(), 31);
      sb.append(dest).append(padRight("", 31 - dest.length()));
      sb.append(" ");

      if (conn.status() == ConnectionRecord.Status.OK) {
        String transfer =
            "↑" + formatBytes(conn.bytesUp()) + " ↓" + formatBytes(conn.bytesDown());
        sb.append(CYAN).append(transfer).append(RESET);
        sb.append(padRight("", 19 - transfer.length()));
      } else {
        sb.append(DIM).append("-").append(RESET);
        sb.append(padRight("", 18));
      }

      sb.append(DIM).append("║").append(RESET).append("\n");
    }

    // Empty rows to fill space
    for (int i = rows; i < MAX_VISIBLE_CONNECTIONS; i++) {
      sb.append(DIM).append("║").append(" ".repeat(WIDTH)).append("║").append(RESET).append("\n");
    }

    // Footer
    sb.append(DIM).append("╚").append("═".repeat(WIDTH)).append("╝").append(RESET).append("\n");
    sb.append(DIM).append("  Press Ctrl+C to stop").append(RESET);

    out.print(sb);
    out.flush();
  }

  private String label(String text) {
    return DIM + text + "  " + RESET;
  }

  private String padTo(String text, int totalWidth) {
    int padding = Math.max(0, totalWidth - text.length());
    return " ".repeat(padding) + text;
  }

  private String padRight(String text, int totalWidth) {
    int padding = Math.max(0, totalWidth - text.length());
    return text + " ".repeat(padding);
  }

  private String truncate(String text, int maxLen) {
    if (text.length() <= maxLen) return text;
    return text.substring(0, maxLen - 1) + "…";
  }

  static String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + "B";
    if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1fK", bytes / 1024.0);
    if (bytes < 1024L * 1024 * 1024)
      return String.format(java.util.Locale.US, "%.1fM", bytes / (1024.0 * 1024));
    return String.format(java.util.Locale.US, "%.1fG", bytes / (1024.0 * 1024 * 1024));
  }

  static String formatUptime(Duration duration) {
    long hours = duration.toHours();
    long minutes = duration.toMinutesPart();
    long seconds = duration.toSecondsPart();
    return String.format("%02d:%02d:%02d", hours, minutes, seconds);
  }
}
