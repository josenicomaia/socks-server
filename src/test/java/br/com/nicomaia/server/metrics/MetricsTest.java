package br.com.nicomaia.server.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsTest {

  private Metrics metrics;

  @BeforeEach
  void setUp() {
    metrics = Metrics.instance();
    // Reset by opening/closing connections to bring counters back
  }

  @Test
  void shouldTrackConnectionOpened() {
    long before = metrics.totalConnections();
    int activeBefore = metrics.activeConnections();

    metrics.connectionOpened();

    assertEquals(before + 1, metrics.totalConnections());
    assertEquals(activeBefore + 1, metrics.activeConnections());

    // Cleanup
    metrics.connectionClosed();
  }

  @Test
  void shouldDecrementActiveOnClose() {
    metrics.connectionOpened();
    int active = metrics.activeConnections();

    metrics.connectionClosed();

    assertEquals(active - 1, metrics.activeConnections());
  }

  @Test
  void shouldTrackBytes() {
    long upBefore = metrics.bytesUploaded();
    long downBefore = metrics.bytesDownloaded();

    metrics.addBytesUploaded(1024);
    metrics.addBytesDownloaded(2048);

    assertEquals(upBefore + 1024, metrics.bytesUploaded());
    assertEquals(downBefore + 2048, metrics.bytesDownloaded());
  }

  @Test
  void shouldStoreRecentConnections() {
    ConnectionRecord record =
        new ConnectionRecord(
            LocalTime.now(), "example.com:443", ConnectionRecord.Status.OK, 100, 200);

    metrics.addConnectionRecord(record);

    List<ConnectionRecord> recent = metrics.recentConnections();
    assertFalse(recent.isEmpty());
    assertEquals("example.com:443", recent.getFirst().destination());
  }

  @Test
  void shouldLimitRecentConnectionsTo20() {
    for (int i = 0; i < 25; i++) {
      metrics.addConnectionRecord(
          new ConnectionRecord(
              LocalTime.now(), "host-" + i + ":80", ConnectionRecord.Status.OK, 0, 0));
    }

    assertEquals(20, metrics.recentConnections().size());
    // Most recent should be first
    assertEquals("host-24:80", metrics.recentConnections().getFirst().destination());
  }

  @Test
  void shouldReturnImmutableCopyOfRecentConnections() {
    metrics.addConnectionRecord(
        new ConnectionRecord(
            LocalTime.now(), "test.com:443", ConnectionRecord.Status.OK, 0, 0));

    List<ConnectionRecord> copy = metrics.recentConnections();
    assertThrows(UnsupportedOperationException.class, () -> copy.clear());
  }

  @Test
  void shouldHaveStartTime() {
    assertNotNull(metrics.startTime());
  }
}
