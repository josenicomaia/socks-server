package br.com.nicomaia.server.protocol;

import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressType;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class SocketReader {

  public static Address readAddress(AddressType addressType, InputStream in) throws IOException {
    int length;

    if (AddressType.IPV6 == addressType) {
      length = 16;
    } else if (AddressType.IPV4 == addressType) {
      length = 4;
    } else if (AddressType.DOMAIN_NAME == addressType) {
      length = readDomainLength(in);
    } else {
      throw new IOException("Unsupported address type: " + addressType);
    }

    return new Address(readFully(in, length), addressType);
  }

  public static int readPort(InputStream in) throws IOException {
    byte[] buffer = readFully(in, 2);
    return ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
  }

  private static int readDomainLength(InputStream in) throws IOException {
    return readFully(in, 1)[0] & 0xFF;
  }

  private static byte[] readFully(InputStream in, int length) throws IOException {
    byte[] buffer = in.readNBytes(length);
    if (buffer.length != length) {
      throw new EOFException("Unexpected EOF: expected " + length + " bytes, got " + buffer.length);
    }
    return buffer;
  }
}
