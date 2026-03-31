package br.com.nicomaia.server.protocol;

import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressType;
import java.io.IOException;
import java.io.InputStream;

public class SocketReader {

  public static Address readAddress(AddressType addressType, InputStream in) throws IOException {
    byte[] buffer;

    if (AddressType.IPV6 == addressType) {
      buffer = new byte[16];
    } else if (AddressType.IPV4 == addressType) {
      buffer = new byte[4];
    } else if (AddressType.DOMAIN_NAME == addressType) {
      buffer = new byte[readDomainLength(in)];
    } else {
      throw new IOException("Unsupported address type: " + addressType);
    }

    in.read(buffer);
    return new Address(buffer, addressType);
  }

  public static int readPort(InputStream in) throws IOException {
    byte[] buffer = new byte[2];
    in.read(buffer);
    return ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
  }

  private static byte readDomainLength(InputStream in) throws IOException {
    byte[] buffer = new byte[1];
    in.read(buffer);
    return buffer[0];
  }
}
