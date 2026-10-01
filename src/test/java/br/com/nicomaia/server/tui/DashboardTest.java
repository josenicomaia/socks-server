package br.com.nicomaia.server.tui;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.metrics.Metrics;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DashboardTest {

  private static Dashboard silentDashboard() {
    return new Dashboard(
        5353, "1.0.0", Metrics.instance(), new PrintStream(OutputStream.nullOutputStream()));
  }

  @Test
  void shouldShowWarningsInEveryFrame() {
    var dashboard = silentDashboard().withWarning("! AUTHENTICATION DISABLED");

    assertTrue(dashboard.frame().contains("! AUTHENTICATION DISABLED"));
  }

  @Test
  void shouldNotShowWarningsByDefault() {
    assertFalse(silentDashboard().frame().contains("AUTHENTICATION DISABLED"));
  }

  @Test
  void shouldRejectWarningsThatDoNotFitTheFrame() {
    var dashboard = silentDashboard();

    assertThrows(IllegalArgumentException.class, () -> dashboard.withWarning("x".repeat(100)));
  }

  @Test
  void shouldFormatBytesUnderKB() {
    assertEquals("0B", Dashboard.formatBytes(0));
    assertEquals("512B", Dashboard.formatBytes(512));
    assertEquals("1023B", Dashboard.formatBytes(1023));
  }

  @Test
  void shouldFormatBytesAsKB() {
    assertEquals("1.0K", Dashboard.formatBytes(1024));
    assertEquals("1.5K", Dashboard.formatBytes(1536));
  }

  @Test
  void shouldFormatBytesAsMB() {
    assertEquals("1.0M", Dashboard.formatBytes(1024 * 1024));
    assertEquals("12.4M", Dashboard.formatBytes((long) (12.4 * 1024 * 1024)));
  }

  @Test
  void shouldFormatBytesAsGB() {
    assertEquals("1.0G", Dashboard.formatBytes(1024L * 1024 * 1024));
  }

  @Test
  void shouldFormatUptimeWithHoursMinutesSeconds() {
    assertEquals("00:00:00", Dashboard.formatUptime(Duration.ZERO));
    assertEquals("00:01:30", Dashboard.formatUptime(Duration.ofSeconds(90)));
    assertEquals("01:00:00", Dashboard.formatUptime(Duration.ofHours(1)));
    assertEquals(
        "25:30:15", Dashboard.formatUptime(Duration.ofHours(25).plusMinutes(30).plusSeconds(15)));
  }
}
