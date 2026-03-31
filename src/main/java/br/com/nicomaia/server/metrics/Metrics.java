package br.com.nicomaia.server.metrics;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Metrics {

  private static final Metrics INSTANCE = new Metrics();
  private static final int MAX_RECENT_CONNECTIONS = 20;

  private final Instant startTime = Instant.now();
  private final AtomicLong totalConnections = new AtomicLong(0);
  private final AtomicInteger activeConnections = new AtomicInteger(0);
  private final AtomicLong bytesUploaded = new AtomicLong(0);
  private final AtomicLong bytesDownloaded = new AtomicLong(0);
  private final LinkedList<ConnectionRecord> recentConnections = new LinkedList<>();

  private Metrics() {}

  public static Metrics instance() {
    return INSTANCE;
  }

  public void connectionOpened() {
    totalConnections.incrementAndGet();
    activeConnections.incrementAndGet();
  }

  public void connectionClosed() {
    activeConnections.decrementAndGet();
  }

  public void addBytesUploaded(long bytes) {
    bytesUploaded.addAndGet(bytes);
  }

  public void addBytesDownloaded(long bytes) {
    bytesDownloaded.addAndGet(bytes);
  }

  public void addConnectionRecord(ConnectionRecord record) {
    synchronized (recentConnections) {
      recentConnections.addFirst(record);
      while (recentConnections.size() > MAX_RECENT_CONNECTIONS) {
        recentConnections.removeLast();
      }
    }
  }

  public Instant startTime() {
    return startTime;
  }

  public long totalConnections() {
    return totalConnections.get();
  }

  public int activeConnections() {
    return activeConnections.get();
  }

  public long bytesUploaded() {
    return bytesUploaded.get();
  }

  public long bytesDownloaded() {
    return bytesDownloaded.get();
  }

  public List<ConnectionRecord> recentConnections() {
    synchronized (recentConnections) {
      return List.copyOf(recentConnections);
    }
  }
}
