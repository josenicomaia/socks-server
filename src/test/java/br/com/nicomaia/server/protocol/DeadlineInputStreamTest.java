package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeadlineInputStreamTest {

  private final MutableClock clock = new MutableClock();
  private final RecordingSocket socket = new RecordingSocket();

  @Test
  void shouldNarrowSoTimeoutToTimeLeftBeforeEachRead() throws IOException {
    InputStream in = deadlineStream(new byte[] {1, 2, 3}, Duration.ofSeconds(10));

    in.read();
    assertEquals(10_000, socket.lastSoTimeout);

    clock.advance(Duration.ofSeconds(4));
    in.read();
    assertEquals(6_000, socket.lastSoTimeout);

    clock.advance(Duration.ofMillis(5_999));
    in.read(new byte[1], 0, 1);
    assertEquals(1, socket.lastSoTimeout);
  }

  @Test
  void shouldFailOnceDeadlineHasPassedEvenIfDataIsAvailable() {
    InputStream in = deadlineStream(new byte[] {1, 2, 3}, Duration.ofSeconds(10));

    clock.advance(Duration.ofSeconds(10));

    assertThrows(SocketTimeoutException.class, in::read);
  }

  @Test
  void shouldApplyDeadlineToReadNBytes() throws IOException {
    InputStream in = deadlineStream(new byte[] {1, 2, 3}, Duration.ofSeconds(10));
    clock.advance(Duration.ofSeconds(3));

    assertArrayEquals(new byte[] {1, 2}, in.readNBytes(2));
    assertEquals(7_000, socket.lastSoTimeout);
  }

  private InputStream deadlineStream(byte[] data, Duration budget) {
    return new DeadlineInputStream(socket, new ByteArrayInputStream(data), budget, clock);
  }

  /** Unconnected socket that only records the timeouts it is given. */
  private static final class RecordingSocket extends Socket {
    private int lastSoTimeout = -1;

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
      lastSoTimeout = timeout;
    }
  }
}
