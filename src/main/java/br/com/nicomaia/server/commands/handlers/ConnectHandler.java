package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandResponse;
import br.com.nicomaia.server.commands.FailureCommandResponse;
import br.com.nicomaia.server.commands.SuccessCommandResponse;
import br.com.nicomaia.server.metrics.ConnectionRecord;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.transfer.ClientServerTransfer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectHandler implements CommandHandler {

  private static final Logger logger = Logger.getLogger(ConnectHandler.class.getName());

  /** Without it, a destination that drops SYNs keeps the client waiting for the OS default. */
  static final int CONNECT_TIMEOUT_MILLIS = 10_000;

  private final Metrics metrics;

  public ConnectHandler(Metrics metrics) {
    this.metrics = metrics;
  }

  public void handle(Socket client, Command command) {
    String destination = command.address().getHostName() + ":" + command.port();

    Socket proxiedConnection = new Socket();
    try {
      proxiedConnection.connect(
          new InetSocketAddress(command.address(), command.port()), CONNECT_TIMEOUT_MILLIS);
    } catch (IOException e) {
      closeQuietly(proxiedConnection);
      logger.log(Level.WARNING, "Connect failed to " + destination, e);
      recordFailure(destination);
      trySendFailure(client, command);
      return;
    }

    // Send the SOCKS success reply before starting the relay: otherwise a destination
    // that writes immediately could have its bytes forwarded to the client ahead of the
    // reply, corrupting the protocol stream.
    try {
      sendResponse(client, new SuccessCommandResponse(command, proxiedConnection));
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to send success response for " + destination, e);
      closeQuietly(proxiedConnection);
      recordFailure(destination);
      return;
    }

    // Record before the relay: transfer.start() blocks for the connection's whole lifetime.
    metrics.addConnectionRecord(
        new ConnectionRecord(LocalTime.now(), destination, ConnectionRecord.Status.OK, 0, 0));

    ClientServerTransfer transfer = new ClientServerTransfer(client, proxiedConnection, metrics);
    transfer.start();
  }

  private void recordFailure(String destination) {
    metrics.addConnectionRecord(
        new ConnectionRecord(LocalTime.now(), destination, ConnectionRecord.Status.FAIL, 0, 0));
  }

  private void trySendFailure(Socket client, Command command) {
    try {
      sendResponse(client, new FailureCommandResponse(command));
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Failed to send error response", ex);
    }
  }

  private void sendResponse(Socket client, CommandResponse response) throws IOException {
    logger.info(response.toString());

    client.getOutputStream().write(response.getBytes(client));
    client.getOutputStream().flush();
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
