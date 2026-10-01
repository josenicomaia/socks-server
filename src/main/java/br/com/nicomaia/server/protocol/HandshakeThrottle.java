package br.com.nicomaia.server.protocol;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-client limits on unauthenticated handshakes. A client is an IPv4 address or an IPv6 /64
 * (the smallest block normally assigned to one subscriber, so rotating addresses inside it gains
 * nothing).
 *
 * <ul>
 *   <li>At most {@code maxConcurrentHandshakes} handshakes in flight per client, so one host
 *       can't pin an unbounded number of sockets in the handshake phase.
 *   <li>At most {@code maxFailures} credential checks per {@code window}, counting failed ones
 *       and those still being evaluated. A check must be reserved with {@link
 *       #tryBeginAttempt(InetAddress)} before the password is compared, so opening many
 *       connections in parallel doesn't buy extra guesses. Once {@code maxFailures} checks have
 *       failed, the client is refused until the window that began with its first counted
 *       failure has elapsed.
 * </ul>
 *
 * <p>Clients sharing one address (NAT) share these limits; legitimate clients only notice them
 * if someone behind the same address is guessing passwords.
 */
public final class HandshakeThrottle {

  /** Result of {@link #tryAcquire(InetAddress)}. */
  public enum Admission {
    ADMITTED,
    /** Refused: too many failed logins in the current window (reported by recordFailure). */
    BLOCKED,
    /** Refused: concurrent handshake cap reached; first refusal for this client in the window. */
    CAPACITY_REACHED,
    /** Refused: concurrent handshake cap reached; already reported in the current window. */
    AT_CAPACITY;

    public boolean isAdmitted() {
      return this == ADMITTED;
    }
  }

  /**
   * Generous on purpose: a legitimate client (a browser behind the proxy, or several hosts
   * behind one NAT address) opens many connections at once, and each spends only milliseconds in
   * the handshake. Password guesses are bounded separately by {@code maxFailures}.
   */
  static final int DEFAULT_MAX_CONCURRENT_HANDSHAKES = 64;

  static final int DEFAULT_MAX_FAILURES = 5;
  static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

  /** Above this many tracked clients, idle entries are purged... */
  static final int DEFAULT_PURGE_THRESHOLD = 10_000;

  /** ...but at most this often, so the O(n) sweep never runs on every connection. */
  static final Duration PURGE_INTERVAL = Duration.ofSeconds(10);

  private final int maxConcurrentHandshakes;
  private final int maxFailures;
  private final Duration window;
  private final int purgeThreshold;
  private final Clock clock;
  private final Map<InetAddress, State> states = new ConcurrentHashMap<>();
  private final AtomicLong lastPurgeMillis = new AtomicLong(Long.MIN_VALUE);

  public HandshakeThrottle() {
    this(
        DEFAULT_MAX_CONCURRENT_HANDSHAKES,
        DEFAULT_MAX_FAILURES,
        DEFAULT_WINDOW,
        DEFAULT_PURGE_THRESHOLD,
        Clock.systemUTC());
  }

  HandshakeThrottle(
      int maxConcurrentHandshakes,
      int maxFailures,
      Duration window,
      int purgeThreshold,
      Clock clock) {
    this.maxConcurrentHandshakes = maxConcurrentHandshakes;
    this.maxFailures = maxFailures;
    this.window = window;
    this.purgeThreshold = purgeThreshold;
    this.clock = clock;
  }

  /**
   * Reserves a handshake slot. Every admitted result must be paired with exactly one {@link
   * #release(InetAddress)}.
   */
  public Admission tryAcquire(InetAddress address) {
    purgeIfLarge();
    Instant now = clock.instant();
    Admission[] admission = {null};
    states.compute(
        clientKey(address),
        (key, state) -> {
          State current = (state == null) ? new State() : state;
          current.expire(now, window);
          if (current.failures >= maxFailures) {
            admission[0] = Admission.BLOCKED;
          } else if (current.inFlight >= maxConcurrentHandshakes) {
            boolean reported = current.capacityReportedAt != null; // expire() cleared stale ones
            if (!reported) {
              current.capacityReportedAt = now;
            }
            admission[0] = reported ? Admission.AT_CAPACITY : Admission.CAPACITY_REACHED;
          } else {
            current.inFlight++;
            admission[0] = Admission.ADMITTED;
          }
          return current.isIdle() ? null : current;
        });
    return admission[0];
  }

  public void release(InetAddress address) {
    states.computeIfPresent(
        clientKey(address),
        (key, state) -> {
          state.inFlight--;
          return state.isIdle() ? null : state;
        });
  }

  /**
   * Reserves one credential check. Must be followed by exactly one {@link #recordFailure} or
   * {@link #recordSuccess} when it returns {@code true}.
   *
   * @return {@code false} if the client's guess budget for the window is exhausted, counting
   *     checks still in progress; the credentials must then be rejected without comparing them
   */
  public boolean tryBeginAttempt(InetAddress address) {
    Instant now = clock.instant();
    boolean[] reserved = {false};
    states.compute(
        clientKey(address),
        (key, state) -> {
          State current = (state == null) ? new State() : state;
          current.expire(now, window);
          if (current.failures + current.pendingAttempts < maxFailures) {
            current.pendingAttempts++;
            reserved[0] = true;
          }
          return current.isIdle() ? null : current;
        });
    return reserved[0];
  }

  /**
   * Converts a reserved attempt into a counted failure.
   *
   * @return {@code true} if this failure is the one that blocks the client
   */
  public boolean recordFailure(InetAddress address) {
    Instant now = clock.instant();
    boolean[] blockedNow = {false};
    states.compute(
        clientKey(address),
        (key, state) -> {
          State current = (state == null) ? new State() : state;
          current.expire(now, window);
          current.endAttempt();
          if (current.failures == 0) {
            current.windowStart = now;
          }
          current.failures++;
          blockedNow[0] = current.failures == maxFailures;
          return current;
        });
    return blockedNow[0];
  }

  /** Releases a reserved attempt whose credentials were valid. */
  public void recordSuccess(InetAddress address) {
    states.computeIfPresent(
        clientKey(address),
        (key, state) -> {
          state.endAttempt();
          return state.isIdle() ? null : state;
        });
  }

  Duration window() {
    return window;
  }

  int trackedClients() {
    return states.size();
  }

  /** IPv4 addresses are their own key; IPv6 addresses are reduced to their /64 prefix. */
  static InetAddress clientKey(InetAddress address) {
    if (!(address instanceof Inet6Address)) {
      return address;
    }
    byte[] prefix = Arrays.copyOf(address.getAddress(), 16);
    Arrays.fill(prefix, 8, 16, (byte) 0);
    try {
      return InetAddress.getByAddress(prefix);
    } catch (UnknownHostException e) {
      throw new IllegalStateException("16-byte address rejected", e); // can't happen
    }
  }

  private void purgeIfLarge() {
    if (states.size() <= purgeThreshold) {
      return;
    }
    long now = clock.millis();
    long last = lastPurgeMillis.get();
    if (last != Long.MIN_VALUE && now - last < PURGE_INTERVAL.toMillis()) {
      return;
    }
    if (!lastPurgeMillis.compareAndSet(last, now)) {
      return; // another thread is purging
    }
    Instant instant = clock.instant();
    for (InetAddress key : states.keySet()) {
      states.computeIfPresent(
          key,
          (k, state) -> {
            state.expire(instant, window);
            return state.isIdle() ? null : state;
          });
    }
  }

  /** Mutated only inside {@link ConcurrentHashMap#compute}, which serialises per key. */
  private static final class State {
    private int inFlight;
    private int pendingAttempts;
    private int failures;
    private Instant windowStart;
    private Instant capacityReportedAt;

    /** Forgets failures and the capacity report once their window has elapsed. */
    void expire(Instant now, Duration window) {
      if (failures > 0 && !now.isBefore(windowStart.plus(window))) {
        failures = 0;
        windowStart = null;
      }
      if (capacityReportedAt != null && !now.isBefore(capacityReportedAt.plus(window))) {
        capacityReportedAt = null;
      }
    }

    void endAttempt() {
      if (pendingAttempts > 0) {
        pendingAttempts--;
      }
    }

    /**
     * Idle entries are dropped. A pending capacity report keeps the entry alive so the "once per
     * window" warning holds across bursts; once the window elapses it is cleared on the client's
     * next call, or by the purge when the map grows past the threshold.
     */
    boolean isIdle() {
      return inFlight == 0 && pendingAttempts == 0 && failures == 0 && capacityReportedAt == null;
    }
  }
}
