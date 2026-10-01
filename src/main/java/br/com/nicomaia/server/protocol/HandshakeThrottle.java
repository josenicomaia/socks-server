package br.com.nicomaia.server.protocol;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client-address limits on unauthenticated handshakes:
 *
 * <ul>
 *   <li>at most {@code maxConcurrentHandshakes} handshakes in flight per address, so one host
 *       can't pin an unbounded number of sockets in the handshake phase or parallelise
 *       password guesses;
 *   <li>after {@code maxFailures} failed credential checks within {@code window}, the address is
 *       refused until that window has elapsed since its first counted failure.
 * </ul>
 *
 * <p>Clients sharing one address (NAT) share these limits; legitimate clients only notice them
 * if someone behind the same address is guessing passwords.
 */
public final class HandshakeThrottle {

  /**
   * Generous on purpose: a legitimate client (a browser behind the proxy, or several hosts
   * behind one NAT address) opens many connections at once, and each spends only milliseconds in
   * the handshake. It still bounds how many password guesses one address can have in flight.
   */
  static final int DEFAULT_MAX_CONCURRENT_HANDSHAKES = 64;
  static final int DEFAULT_MAX_FAILURES = 5;
  static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

  /** Above this many tracked addresses, idle entries are purged on the next access. */
  private static final int PURGE_THRESHOLD = 10_000;

  private final int maxConcurrentHandshakes;
  private final int maxFailures;
  private final Duration window;
  private final Clock clock;
  private final Map<InetAddress, State> states = new ConcurrentHashMap<>();

  public HandshakeThrottle() {
    this(DEFAULT_MAX_CONCURRENT_HANDSHAKES, DEFAULT_MAX_FAILURES, DEFAULT_WINDOW, Clock.systemUTC());
  }

  HandshakeThrottle(int maxConcurrentHandshakes, int maxFailures, Duration window, Clock clock) {
    this.maxConcurrentHandshakes = maxConcurrentHandshakes;
    this.maxFailures = maxFailures;
    this.window = window;
    this.clock = clock;
  }

  /**
   * Reserves a handshake slot for {@code address}. Every {@code true} result must be paired with
   * exactly one {@link #release(InetAddress)}.
   *
   * @return {@code false} if the address is blocked or already has too many handshakes in flight
   */
  public boolean tryAcquire(InetAddress address) {
    purgeIfLarge();
    Instant now = clock.instant();
    boolean[] acquired = {false};
    states.compute(
        address,
        (key, state) -> {
          State current = (state == null) ? new State() : state;
          current.expireFailures(now, window);
          if (current.failures < maxFailures && current.inFlight < maxConcurrentHandshakes) {
            current.inFlight++;
            acquired[0] = true;
          }
          return current;
        });
    return acquired[0];
  }

  public void release(InetAddress address) {
    states.computeIfPresent(
        address,
        (key, state) -> {
          state.inFlight--;
          return state.isIdle() ? null : state;
        });
  }

  /**
   * Counts a failed credential check against {@code address}.
   *
   * @return {@code true} if this failure is the one that blocks the address
   */
  public boolean recordFailure(InetAddress address) {
    Instant now = clock.instant();
    boolean[] blockedNow = {false};
    states.compute(
        address,
        (key, state) -> {
          State current = (state == null) ? new State() : state;
          current.expireFailures(now, window);
          if (current.failures == 0) {
            current.windowStart = now;
          }
          current.failures++;
          blockedNow[0] = current.failures == maxFailures;
          return current;
        });
    return blockedNow[0];
  }

  Duration window() {
    return window;
  }

  private void purgeIfLarge() {
    if (states.size() <= PURGE_THRESHOLD) {
      return;
    }
    Instant now = clock.instant();
    for (InetAddress address : states.keySet()) {
      states.computeIfPresent(
          address,
          (key, state) -> {
            state.expireFailures(now, window);
            return state.isIdle() ? null : state;
          });
    }
  }

  /** Mutated only inside {@link ConcurrentHashMap#compute}, which serialises per key. */
  private static final class State {
    private int inFlight;
    private int failures;
    private Instant windowStart;

    void expireFailures(Instant now, Duration window) {
      if (failures > 0 && !now.isBefore(windowStart.plus(window))) {
        failures = 0;
        windowStart = null;
      }
    }

    boolean isIdle() {
      return inFlight == 0 && failures == 0;
    }
  }
}
