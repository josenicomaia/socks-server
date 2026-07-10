package br.com.nicomaia.server.protocol;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressResolver;
import br.com.nicomaia.server.net.AddressType;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SocksProtocolHandler {

  private static final Logger logger = Logger.getLogger(SocksProtocolHandler.class.getName());

  private static final byte SOCKS_VERSION = 0x05;
  private static final byte NO_ACCEPTABLE_METHODS = (byte) 0xFF;

  private final AddressResolver addressResolver;
  private final HandlersHolder handlers;

  public SocksProtocolHandler(AddressResolver addressResolver, HandlersHolder handlers) {
    this.addressResolver = addressResolver;
    this.handlers = handlers;
  }

  public void handle(Socket clientSocket) {
    try {
      InputStream in = clientSocket.getInputStream();

      // --- Auth Negotiation ---
      byte[] buffer = readFully(in, 2);

      byte socksVersion = buffer[0];
      byte availableClientAuthTypes = buffer[1];

      if (socksVersion != SOCKS_VERSION) {
        logger.info("Rejecting connection with unsupported SOCKS version: " + socksVersion);
        closeQuietly(clientSocket);
        return;
      }

      buffer = readFully(in, availableClientAuthTypes & 0xFF);

      var authRequest =
          new AuthRequest(
              socksVersion, availableClientAuthTypes, SupportedAuthType.valueOf(buffer));
      logger.info(authRequest.toString());

      if (!authRequest.supportedAuthTypes().contains(SupportedAuthType.NO_AUTH)) {
        logger.info("Client did not offer NO_AUTH; rejecting with NO ACCEPTABLE METHODS");
        clientSocket.getOutputStream().write(new byte[] {socksVersion, NO_ACCEPTABLE_METHODS});
        clientSocket.getOutputStream().flush();
        closeQuietly(clientSocket);
        return;
      }

      var authResponse = new AuthResponse(socksVersion, SupportedAuthType.NO_AUTH);
      logger.info(authResponse.toString());

      clientSocket.getOutputStream().write(authResponse.toBytes());

      // --- Command ---
      buffer = readFully(in, 4);

      socksVersion = buffer[0];
      CommandType commandType = CommandType.valueOf(buffer[1]);
      AddressType addressType = AddressType.valueOf(buffer[3]);

      Address address = SocketReader.readAddress(addressType, in);
      InetAddress inetAddress = addressResolver.resolve(address);
      int port = SocketReader.readPort(in);

      var command = new Command(socksVersion, commandType, addressType, inetAddress, port);
      logger.info(command.toString());

      handlers.get(commandType).handle(clientSocket, command);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Error handling SOCKS connection", e);
      closeQuietly(clientSocket);
    }
  }

  private static byte[] readFully(InputStream in, int length) throws IOException {
    byte[] buffer = in.readNBytes(length);
    if (buffer.length != length) {
      throw new EOFException("Unexpected EOF: expected " + length + " bytes, got " + buffer.length);
    }
    return buffer;
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
