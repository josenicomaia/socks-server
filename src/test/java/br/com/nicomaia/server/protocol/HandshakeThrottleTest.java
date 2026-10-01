package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HandshakeThrottleTest {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final MutableClock clock = new MutableClock();
  private final HandshakeThrottle throttle = new HandshakeThrottle(2, 3, WINDOW, clock);
  private final InetAddress client = address(10, 0, 0, 1);

  @Test
  void shouldLimitConcurrentHandshakesPerAddress() {
    assertTrue(throttle.tryAcquire(client));
    assertTrue(throttle.tryAcquire(client));

    assertFalse(throttle.tryAcquire(client), "third concurrent handshake must be refused");

    throttle.release(client);
    assertTrue(throttle.tryAcquire(client), "a released slot can be reused");
  }

  @Test
  void shouldNotLetOneAddressConsumeAnotherAddressSlots() {
    throttle.tryAcquire(client);
    throttle.tryAcquire(client);

    assertTrue(throttle.tryAcquire(address(10, 0, 0, 2)));
  }

  @Test
  void shouldBlockAddressAfterMaxFailuresWithinWindow() {
    assertFalse(fail(client));
    assertFalse(fail(client));
    assertTrue(fail(client), "the failure reaching the limit reports the block");

    assertFalse(throttle.tryAcquire(client));
  }

  @Test
  void shouldUnblockOnceWindowSinceFirstFailureElapses() {
    fail(client);
    clock.advance(Duration.ofSeconds(30));
    fail(client);
    fail(client);
    assertFalse(throttle.tryAcquire(client));

    clock.advance(Duration.ofSeconds(30)); // one window after the first counted failure

    assertTrue(throttle.tryAcquire(client));
  }

  @Test
  void shouldForgetFailuresOlderThanTheWindow() {
    fail(client);
    fail(client);
    clock.advance(WINDOW);

    assertFalse(fail(client), "stale failures must not count towards a new block");
    assertTrue(throttle.tryAcquire(client));
  }

  @Test
  void shouldKeepCountingFailuresWhileHandshakesAreInFlight() {
    assertTrue(throttle.tryAcquire(client));
    fail(client);
    throttle.release(client);
    fail(client);
    fail(client);

    assertFalse(throttle.tryAcquire(client));
  }

  /** Simulates one failed login: acquire, record the failure, release. */
  private boolean fail(InetAddress address) {
    throttle.tryAcquire(address);
    try {
      return throttle.recordFailure(address);
    } finally {
      throttle.release(address);
    }
  }

  private static InetAddress address(int a, int b, int c, int d) {
    try {
      return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
    } catch (java.net.UnknownHostException e) {
      throw new AssertionError(e);
    }
  }
}
