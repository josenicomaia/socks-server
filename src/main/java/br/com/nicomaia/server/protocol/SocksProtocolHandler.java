package br.com.nicomaia.server.protocol;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressResolver;
import br.com.nicomaia.server.net.AddressType;
import br.com.nicomaia.server.net.ResolverNotFoundException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SocksProtocolHandler {

  private static final Logger logger = Logger.getLogger(SocksProtocolHandler.class.getName());

  private static final byte RESERVED = 0x00;

  /**
   * Total time a client gets to send everything it owes during the handshake: greeting,
   * credentials and command request. It is an absolute deadline across all reads, not a per-read
   * timeout. It does not cover resolving the requested domain name or connecting to the
   * destination, which happen on the server side after the command request has been read.
   */
  static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);

  private final AddressResolver addressResolver;
  private final HandlersHolder handlers;
  private final Socks5Authenticator authenticator;
  private final HandshakeThrottle throttle;
  private final Duration handshakeTimeout;
  private final Clock clock;

  public SocksProtocolHandler(
      AddressResolver addressResolver,
      HandlersHolder handlers,
      Socks5Authenticator authenticator,
      HandshakeThrottle throttle) {
    this(
        addressResolver,
        handlers,
        authenticator,
        throttle,
        DEFAULT_HANDSHAKE_TIMEOUT,
        Clock.systemUTC());
  }

  SocksProtocolHandler(
      AddressResolver addressResolver,
      HandlersHolder handlers,
      Socks5Authenticator authenticator,
      HandshakeThrottle throttle,
      Duration handshakeTimeout,
      Clock clock) {
    this.addressResolver = addressResolver;
    this.handlers = handlers;
    this.authenticator = authenticator;
    this.throttle = throttle;
    this.handshakeTimeout = handshakeTimeout;
    this.clock = clock;
  }

  public void handle(Socket clientSocket) {
    InetAddress peer = clientSocket.getInetAddress();
    var admission = throttle.tryAcquire(peer);
    if (!admission.isAdmitted()) {
      if (admission == HandshakeThrottle.Admission.CAPACITY_REACHED) {
        // Once per client per window, so operators can diagnose bursts or busy NAT addresses
        // without a flooding host flooding the log.
        logger.warning(
            "Refusing connections from "
                + peer
                + ": too many concurrent handshakes (further refusals in the next "
                + throttle.window()
                + " are logged at FINE)");
      } else {
        logger.fine(() -> "Refusing connection from " + peer + ": " + admission);
      }
      closeQuietly(clientSocket);
      return;
    }

    Command command;
    try {
      command = performHandshake(clientSocket, peer);
    } finally {
      throttle.release(peer);
    }

    if (command != null) {
      relay(clientSocket, command);
    }
  }

  /** @return the client's command, or {@code null} if the handshake failed (socket closed). */
  private Command performHandshake(Socket clientSocket, InetAddress peer) {
    try {
      InputStream in = new DeadlineInputStream(clientSocket, handshakeTimeout, clock);

      var outcome =
          authenticator.authenticate(in, clientSocket.getOutputStream(), attemptBudget(peer));
      if (!outcome.isAccepted()) {
        if (outcome == Socks5Authenticator.Outcome.THROTTLED) {
          // The block itself was already logged once; repeating it per attempt would flood.
          logger.fine(() -> "Rejected SOCKS handshake from " + peer + ": " + outcome);
        } else {
          // Never log the username: a password pasted into the username field would leak.
          logger.warning(
              "Rejected SOCKS handshake from "
                  + clientSocket.getRemoteSocketAddress()
                  + ": "
                  + outcome);
        }
        closeQuietly(clientSocket);
        return null;
      }

      Command command = readCommand(clientSocket, in);
      if (command == null) {
        closeQuietly(clientSocket);
      }
      return command;
    } catch (SocketTimeoutException e) {
      logger.info(
          "Closing connection from "
              + clientSocket.getRemoteSocketAddress()
              + ": handshake not completed within "
              + handshakeTimeout);
    } catch (EOFException | SocketException e) {
      // Client hung up (FIN or RST) mid-handshake: scanners and impatient clients do this a lot.
      logger.fine(() -> "Client disconnected during handshake: " + clientSocket.getRemoteSocketAddress());
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error handling SOCKS handshake", e);
    }
    closeQuietly(clientSocket);
    return null;
  }

  /** Each password comparison spends one of the client's guesses in the throttle window. */
  private Socks5Authenticator.AttemptBudget attemptBudget(InetAddress peer) {
    return new Socks5Authenticator.AttemptBudget() {
      @Override
      public boolean tryReserve() {
        return throttle.tryBeginAttempt(peer);
      }

      @Override
      public void complete(boolean credentialsValid) {
        if (credentialsValid) {
          throttle.recordSuccess(peer);
        } else if (throttle.recordFailure(peer)) {
          logger.warning(
              "Blocking " + peer + " for " + throttle.window() + " after repeated failed logins");
        }
      }
    };
  }

  /** @return the parsed command, or {@code null} if the request header is invalid. */
  private Command readCommand(Socket clientSocket, InputStream in)
      throws IOException, ResolverNotFoundException {
    byte[] buffer = SocketReader.readFully(in, 4);

    byte socksVersion = buffer[0];
    if (socksVersion != Socks5Authenticator.SOCKS_VERSION || buffer[2] != RESERVED) {
      logger.warning(
          "Rejected SOCKS request from "
              + clientSocket.getRemoteSocketAddress()
              + ": invalid VER/RSV "
              + socksVersion
              + "/"
              + buffer[2]);
      return null;
    }

    CommandType commandType = CommandType.valueOf(buffer[1]);
    AddressType addressType = AddressType.valueOf(buffer[3]);

    Address address = SocketReader.readAddress(addressType, in);
    InetAddress inetAddress = addressResolver.resolve(address);
    int port = SocketReader.readPort(in);

    var command = new Command(socksVersion, commandType, addressType, inetAddress, port);
    logger.info(command.toString());
    return command;
  }

  private void relay(Socket clientSocket, Command command) {
    try {
      // Handshake is over: the relay may legitimately sit idle, so lift the handshake timeout.
      clientSocket.setSoTimeout(0);
      handlers.get(command.commandType()).handle(clientSocket, command);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error handling SOCKS connection", e);
      closeQuietly(clientSocket);
    }
  }

  private void closeQuietly(Socket socket) {
    try {
      if (!socket.isClosed()) {
        socket.close();
      }
    } catch (IOException e) {
      logger.log(Level.FINE, "Error closing socket", e);
    }
  }
}
