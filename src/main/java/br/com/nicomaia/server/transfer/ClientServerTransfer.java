package br.com.nicomaia.server.transfer;

import br.com.nicomaia.server.metrics.Metrics;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientServerTransfer {
  private static final int DEFAULT_BUFFER_SIZE = 8192;
  private static final Logger logger = Logger.getLogger(ClientServerTransfer.class.getName());

  private final Socket client;
  private final Socket server;
  private final Metrics metrics;

  public ClientServerTransfer(Socket client, Socket server, Metrics metrics) {
    this.client = client;
    this.server = server;
    this.metrics = metrics;
  }

  public void start() {
    Thread.ofVirtual()
        .name(client + " => " + server)
        .start(() -> transfer(client, server, true));

    Thread.ofVirtual()
        .name(client + " <= " + server)
        .start(() -> transfer(server, client, false));
  }

  private void transfer(Socket source, Socket destination, boolean isUpload) {
    try {
      InputStream in = source.getInputStream();
      OutputStream out = destination.getOutputStream();
      byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
      int read;

      while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
        out.write(buffer, 0, read);
        out.flush();
        if (isUpload) {
          metrics.addBytesUploaded(read);
        } else {
          metrics.addBytesDownloaded(read);
        }
      }
    } catch (IOException e) {
      logger.log(Level.FINE, "Transfer ended: " + e.getMessage());
    } finally {
      closeQuietly(client);
      closeQuietly(server);
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
