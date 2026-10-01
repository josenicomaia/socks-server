package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressType;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SocketReaderTest {

  @Test
  void shouldReadIpv4Address() throws IOException {
    InputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3, 4});

    Address address = SocketReader.readAddress(AddressType.IPV4, in);

    assertArrayEquals(new byte[] {1, 2, 3, 4}, address.content());
  }

  @Test
  void shouldReadIpv6Address() throws IOException {
    byte[] payload = new byte[16];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = (byte) i;
    }
    InputStream in = new ByteArrayInputStream(payload);

    Address address = SocketReader.readAddress(AddressType.IPV6, in);

    assertArrayEquals(payload, address.content());
  }

  @Test
  void shouldReadDomainNameAddress() throws IOException {
    byte[] domain = "example.com".getBytes(StandardCharsets.US_ASCII);
    byte[] payload = new byte[domain.length + 1];
    payload[0] = (byte) domain.length;
    System.arraycopy(domain, 0, payload, 1, domain.length);
    InputStream in = new ByteArrayInputStream(payload);

    Address address = SocketReader.readAddress(AddressType.DOMAIN_NAME, in);

    assertArrayEquals(domain, address.content());
  }

  @Test
  void shouldReadDomainLengthGreaterThan127WithoutNegativeArraySize() throws IOException {
    // A domain length byte >= 0x80 is negative as a signed byte; readDomainLength must mask it.
    byte[] domain = new byte[200];
    java.util.Arrays.fill(domain, (byte) 'a');
    byte[] payload = new byte[201];
    payload[0] = (byte) 200;
    System.arraycopy(domain, 0, payload, 1, domain.length);
    InputStream in = new ByteArrayInputStream(payload);

    Address address = SocketReader.readAddress(AddressType.DOMAIN_NAME, in);

    assertEquals(200, address.content().length);
  }

  @Test
  void shouldReadPort() throws IOException {
    InputStream in = new ByteArrayInputStream(new byte[] {0x1F, (byte) 0x90});

    int port = SocketReader.readPort(in);

    assertEquals(8080, port);
  }

  @Test
  void shouldReadFullyAcrossMultiplePartialReads() throws IOException {
    InputStream slowStream =
        new InputStream() {
          private final byte[] data = {1, 2, 3, 4, 5};
          private int position = 0;

          @Override
          public int read() {
            return position < data.length ? data[position++] : -1;
          }

          @Override
          public int read(byte[] b, int off, int len) {
            if (position >= data.length) {
              return -1;
            }
            // Simulate a socket that only ever hands back one byte per call.
            b[off] = data[position++];
            return 1;
          }
        };

    byte[] result = SocketReader.readFully(slowStream, 5);

    assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, result);
  }

  @Test
  void shouldThrowEofExceptionWhenStreamEndsEarly() {
    InputStream in = new ByteArrayInputStream(new byte[] {1, 2});

    assertThrows(EOFException.class, () -> SocketReader.readFully(in, 5));
  }
}
