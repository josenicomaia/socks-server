package br.com.nicomaia.server.transfer;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.metrics.Metrics;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class ClientServerTransferTest {

  @Test
  void shouldBlockUntilBothDirectionsFinishAndCountBytes() throws Exception {
    InetAddress loopback = InetAddress.getLoopbackAddress();
    Metrics metrics = Metrics.instance();

    try (ServerSocket clientListener = new ServerSocket(0, 50, loopback);
        ServerSocket serverListener = new ServerSocket(0, 50, loopback)) {

      Socket transferClientSide = new Socket(loopback, clientListener.getLocalPort());
      Socket testClientSide = clientListener.accept();
      Socket transferServerSide = new Socket(loopback, serverListener.getLocalPort());
      Socket testServerSide = serverListener.accept();
      testClientSide.setSoTimeout(3000);
      testServerSide.setSoTimeout(3000);

      long upBefore = metrics.bytesUploaded();
      long downBefore = metrics.bytesDownloaded();

      ClientServerTransfer transfer =
          new ClientServerTransfer(transferClientSide, transferServerSide, metrics);
      Thread starter = Thread.ofVirtual().start(transfer::start);

      byte[] upload = "hello-upload".getBytes();
      byte[] download = "hi-download".getBytes();

      testClientSide.getOutputStream().write(upload);
      testClientSide.getOutputStream().flush();
      testServerSide.getOutputStream().write(download);
      testServerSide.getOutputStream().flush();

      // Confirm both directions actually relayed the bytes.
      assertArrayEquals(upload, testServerSide.getInputStream().readNBytes(upload.length));
      assertArrayEquals(download, testClientSide.getInputStream().readNBytes(download.length));

      // The relay is live and both sockets are open, so start() must still be blocking.
      assertTrue(starter.isAlive(), "start() must block while the connection is open");

      // Closing both ends makes each direction hit EOF, so start() returns.
      testClientSide.close();
      testServerSide.close();

      starter.join(3000);
      assertFalse(starter.isAlive(), "start() must return only after both directions finish");

      assertTrue(metrics.bytesUploaded() >= upBefore + upload.length);
      assertTrue(metrics.bytesDownloaded() >= downBefore + download.length);
    }
  }
}
