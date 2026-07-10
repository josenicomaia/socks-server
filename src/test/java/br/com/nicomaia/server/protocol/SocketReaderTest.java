package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressType;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class SocketReaderTest {

  /** Wraps a stream so it yields at most one byte per read, simulating TCP fragmentation. */
  private static InputStream drip(byte[] data) {
    return new FilterInputStream(new ByteArrayInputStream(data)) {
      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        return super.read(b, off, Math.min(len, 1));
      }
    };
  }

  @Test
  void shouldReadIpv4AddressAcrossFragmentedReads() throws IOException {
    byte[] data = {10, 0, 0, 1};

    Address address = SocketReader.readAddress(AddressType.IPV4, drip(data));

    assertArrayEquals(data, address.content());
    assertEquals(AddressType.IPV4, address.addressType());
  }

  @Test
  void shouldReadPortAcrossFragmentedReads() throws IOException {
    // 0x1F90 == 8080
    int port = SocketReader.readPort(drip(new byte[] {0x1F, (byte) 0x90}));

    assertEquals(8080, port);
  }

  @Test
  void shouldReadDomainLongerThan127Bytes() throws IOException {
    // A domain length above 127 has the high bit set; read as a signed byte it would be
    // negative and blow up new byte[negative].
    int length = 200;
    byte[] data = new byte[1 + length];
    data[0] = (byte) length; // 0xC8
    for (int i = 0; i < length; i++) {
      data[1 + i] = (byte) 'a';
    }

    Address address =
        SocketReader.readAddress(AddressType.DOMAIN_NAME, new ByteArrayInputStream(data));

    assertEquals(length, address.content().length);
  }

  @Test
  void shouldThrowEofWhenStreamEndsMidAddress() {
    // Only 2 of the 4 expected IPv4 bytes are available.
    InputStream truncated = new ByteArrayInputStream(new byte[] {10, 0});

    assertThrows(EOFException.class, () -> SocketReader.readAddress(AddressType.IPV4, truncated));
  }

  @Test
  void shouldThrowEofWhenStreamEndsMidPort() {
    InputStream truncated = new ByteArrayInputStream(new byte[] {0x1F});

    assertThrows(EOFException.class, () -> SocketReader.readPort(truncated));
  }
}
