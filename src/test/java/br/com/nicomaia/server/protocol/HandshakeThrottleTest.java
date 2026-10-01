package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.protocol.HandshakeThrottle.Admission;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HandshakeThrottleTest {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final MutableClock clock = new MutableClock();
  private final HandshakeThrottle throttle = new HandshakeThrottle(2, 3, WINDOW, 10_000, clock);
  private final InetAddress client = address("10.0.0.1");

  // --- concurrent handshakes ---

  @Test
  void shouldLimitConcurrentHandshakesPerClient() {
    assertEquals(Admission.ADMITTED, throttle.tryAcquire(client));
    assertEquals(Admission.ADMITTED, throttle.tryAcquire(client));

    assertFalse(throttle.tryAcquire(client).isAdmitted(), "third concurrent handshake refused");

    throttle.release(client);
    assertTrue(throttle.tryAcquire(client).isAdmitted(), "a released slot can be reused");
  }

  @Test
  void shouldReportCapacityRefusalOncePerWindow() {
    throttle.tryAcquire(client);
    throttle.tryAcquire(client);

    assertEquals(Admission.CAPACITY_REACHED, throttle.tryAcquire(client));
    assertEquals(Admission.AT_CAPACITY, throttle.tryAcquire(client));

    clock.advance(WINDOW);
    assertEquals(Admission.CAPACITY_REACHED, throttle.tryAcquire(client));
  }

  @Test
  void shouldLetDefaultThrottleAdmitABurstOfLegitimateConnections() {
    // Integration test 8 opens 20 connections at once from one address; a browser behind the
    // proxy does the same. They must not be refused just for arriving together.
    var defaults = new HandshakeThrottle();

    for (int i = 0; i < 20; i++) {
      assertTrue(defaults.tryAcquire(client).isAdmitted(), "connection " + (i + 1) + " refused");
    }
  }

  @Test
  void shouldNotLetOneClientConsumeAnotherClientsSlots() {
    throttle.tryAcquire(client);
    throttle.tryAcquire(client);

    assertTrue(throttle.tryAcquire(address("10.0.0.2")).isAdmitted());
  }

  // --- password guesses ---

  @Test
  void shouldBlockClientAfterMaxFailuresWithinWindow() {
    assertFalse(fail(client));
    assertFalse(fail(client));
    assertTrue(fail(client), "the failure reaching the limit reports the block");

    assertEquals(Admission.BLOCKED, throttle.tryAcquire(client));
    assertFalse(throttle.tryBeginAttempt(client));
  }

  @Test
  void shouldCountInFlightAttemptsAgainstTheGuessBudget() {
    // Three connections reach the password check before any of them has failed.
    assertTrue(throttle.tryBeginAttempt(client));
    assertTrue(throttle.tryBeginAttempt(client));
    assertTrue(throttle.tryBeginAttempt(client));

    assertFalse(throttle.tryBeginAttempt(client), "a fourth parallel guess must not be compared");
  }

  @Test
  void shouldReturnBudgetForSuccessfulAttempts() {
    for (int i = 0; i < 10; i++) {
      assertTrue(throttle.tryBeginAttempt(client));
      throttle.recordSuccess(client);
    }
  }

  @Test
  void shouldCapParallelGuessesAcrossThreadsAtMaxFailures() throws Exception {
    var parallel = new HandshakeThrottle(1_000, 5, WINDOW, 10_000, clock);
    int threads = 64;
    var start = new CountDownLatch(1);
    // No failure is recorded until every thread has tried to reserve: all comparisons overlap,
    // like an attacker pipelining guesses over many connections at once.
    var allTried = new CountDownLatch(threads);
    var compared = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      for (int i = 0; i < threads; i++) {
        pool.submit(
            () -> {
              start.await();
              boolean reserved = parallel.tryBeginAttempt(client);
              allTried.countDown();
              allTried.await();
              if (reserved) {
                compared.incrementAndGet();
                parallel.recordFailure(client);
              }
              return null;
            });
      }
      start.countDown();
    } finally {
      pool.shutdown();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }

    assertEquals(5, compared.get());
  }

  @Test
  void shouldUnblockOnceWindowSinceFirstFailureElapses() {
    fail(client);
    clock.advance(Duration.ofSeconds(30));
    fail(client);
    fail(client);
    assertEquals(Admission.BLOCKED, throttle.tryAcquire(client));

    clock.advance(Duration.ofSeconds(30)); // one window after the first counted failure

    assertTrue(throttle.tryAcquire(client).isAdmitted());
  }

  @Test
  void shouldForgetFailuresOlderThanTheWindow() {
    fail(client);
    fail(client);
    clock.advance(WINDOW);

    assertFalse(fail(client), "stale failures must not count towards a new block");
    assertTrue(throttle.tryAcquire(client).isAdmitted());
  }

  // --- client identity ---

  @Test
  void shouldTreatAnIpv6Slash64AsOneClient() {
    fail(address("2001:db8:1:2::1"));
    fail(address("2001:db8:1:2:aaaa::7"));
    fail(address("2001:db8:1:2:ffff:ffff:ffff:ffff"));

    assertEquals(Admission.BLOCKED, throttle.tryAcquire(address("2001:db8:1:2::42")));
    assertTrue(throttle.tryAcquire(address("2001:db8:1:3::1")).isAdmitted(), "other /64");
  }

  @Test
  void shouldKeepIpv4AddressesDistinct() {
    assertEquals(address("10.0.0.1"), HandshakeThrottle.clientKey(address("10.0.0.1")));
  }

  // --- bookkeeping ---

  @Test
  void shouldForgetIdleClients() {
    assertTrue(throttle.tryAcquire(client).isAdmitted());
    throttle.release(client);
    assertTrue(throttle.tryBeginAttempt(client));
    throttle.recordSuccess(client);

    assertEquals(0, throttle.trackedClients());
  }

  @Test
  void shouldSkipPurgeWithinPurgeInterval() {
    var small = new HandshakeThrottle(2, 3, Duration.ofSeconds(1), 2, clock);
    for (int i = 1; i <= 3; i++) {
      InetAddress address = address("10.0.1." + i);
      small.tryBeginAttempt(address);
      small.recordFailure(address);
    }
    clock.advance(Duration.ofSeconds(1)); // expired
    small.tryAcquire(address("10.0.2.1")); // first purge
    assertEquals(1, small.trackedClients());

    for (int i = 4; i <= 6; i++) {
      InetAddress address = address("10.0.1." + i);
      small.tryBeginAttempt(address);
      small.recordFailure(address);
    }
    clock.advance(Duration.ofSeconds(1)); // expired, but still within PURGE_INTERVAL of last purge
    small.tryAcquire(address("10.0.2.2"));
    assertEquals(5, small.trackedClients(), "no second sweep within PURGE_INTERVAL");

    clock.advance(HandshakeThrottle.PURGE_INTERVAL);
    small.tryAcquire(address("10.0.2.3"));
    assertEquals(3, small.trackedClients(), "only the in-flight handshakes remain");
  }

  /** Simulates one failed login: acquire, spend an attempt, record the failure, release. */
  private boolean fail(InetAddress address) {
    throttle.tryAcquire(address);
    try {
      assertTrue(throttle.tryBeginAttempt(address));
      return throttle.recordFailure(address);
    } finally {
      throttle.release(address);
    }
  }

  private static InetAddress address(String literal) {
    try {
      return InetAddress.getByName(literal); // literals only: no DNS lookup
    } catch (UnknownHostException e) {
      throw new AssertionError(e);
    }
  }
}
