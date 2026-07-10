package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandResponse;
import br.com.nicomaia.server.commands.FailureCommandResponse;
import br.com.nicomaia.server.commands.SuccessCommandResponse;
import br.com.nicomaia.server.metrics.ConnectionRecord;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.transfer.ClientServerTransfer;
import java.io.IOException;
import java.net.Socket;
import java.time.LocalTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectHandler implements CommandHandler {

  private static final Logger logger = Logger.getLogger(ConnectHandler.class.getName());

  private final Metrics metrics;

  public ConnectHandler(Metrics metrics) {
    this.metrics = metrics;
  }

  public void handle(Socket client, Command command) {
    String destination = command.address().getHostName() + ":" + command.port();

    Socket proxiedConnection;
    try {
      proxiedConnection = new Socket(command.address(), command.port());
    } catch (IOException e) {
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

    ClientServerTransfer transfer = new ClientServerTransfer(client, proxiedConnection, metrics);
    transfer.start();

    metrics.addConnectionRecord(
        new ConnectionRecord(LocalTime.now(), destination, ConnectionRecord.Status.OK, 0, 0));
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
