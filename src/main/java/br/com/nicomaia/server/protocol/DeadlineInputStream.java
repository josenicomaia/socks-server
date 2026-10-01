package br.com.nicomaia.server.protocol;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Enforces an absolute deadline across all reads from a socket. {@code SO_TIMEOUT} alone only
 * bounds each individual {@code read()}, so a client dripping one byte just under the timeout
 * could stretch a handshake indefinitely. Before every read this narrows {@code SO_TIMEOUT} to
 * the time left until the deadline, and fails once it has passed.
 */
final class DeadlineInputStream extends FilterInputStream {

  private final Socket socket;
  private final Clock clock;
  private final Instant deadline;

  DeadlineInputStream(Socket socket, Duration budget, Clock clock) throws IOException {
    this(socket, socket.getInputStream(), budget, clock);
  }

  DeadlineInputStream(Socket socket, InputStream in, Duration budget, Clock clock) {
    super(in);
    this.socket = socket;
    this.clock = clock;
    this.deadline = clock.instant().plus(budget);
  }

  @Override
  public int read() throws IOException {
    armTimeout();
    return super.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    armTimeout();
    return super.read(b, off, len);
  }

  private void armTimeout() throws IOException {
    long remainingMillis = Duration.between(clock.instant(), deadline).toMillis();
    if (remainingMillis <= 0) {
      throw new SocketTimeoutException("Handshake deadline exceeded");
    }
    socket.setSoTimeout(Math.toIntExact(Math.min(remainingMillis, Integer.MAX_VALUE)));
  }
}
