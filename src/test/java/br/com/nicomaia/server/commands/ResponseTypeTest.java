package br.com.nicomaia.server.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ResponseTypeTest {

  @Test
  void shouldResolveSucceeded() {
    assertEquals(ResponseType.SUCCEEDED, ResponseType.valueOf((byte) 0x00));
  }

  @Test
  void shouldResolveServerFailure() {
    assertEquals(ResponseType.SOCKS_SERVER_FAILURE, ResponseType.valueOf((byte) 0x01));
  }

  @Test
  void shouldResolveConnectionRefused() {
    assertEquals(ResponseType.CONNECTION_REFUSED, ResponseType.valueOf((byte) 0x05));
  }

  @Test
  void shouldResolveCommandNotSupported() {
    assertEquals(ResponseType.COMMAND_NOT_SUPPORTED, ResponseType.valueOf((byte) 0x07));
  }

  @Test
  void shouldThrowForUnknownResponseType() {
    assertThrows(NoSuchElementException.class, () -> ResponseType.valueOf((byte) 0xFF));
  }

  @Test
  void shouldHaveCorrectByteValues() {
    assertEquals((byte) 0x00, ResponseType.SUCCEEDED.getNumber());
    assertEquals((byte) 0x01, ResponseType.SOCKS_SERVER_FAILURE.getNumber());
    assertEquals((byte) 0x02, ResponseType.CONNECTION_NOT_ALLOWED.getNumber());
    assertEquals((byte) 0x03, ResponseType.NETWORK_UNREACHABLE.getNumber());
    assertEquals((byte) 0x04, ResponseType.HOST_UNREACHABLE.getNumber());
    assertEquals((byte) 0x05, ResponseType.CONNECTION_REFUSED.getNumber());
    assertEquals((byte) 0x06, ResponseType.TTL_EXPIRED.getNumber());
    assertEquals((byte) 0x07, ResponseType.COMMAND_NOT_SUPPORTED.getNumber());
    assertEquals((byte) 0x08, ResponseType.ADDRESS_TYPE_NOT_SUPPORTED.getNumber());
    assertEquals((byte) 0x09, ResponseType.UNASSIGNED.getNumber());
  }
}
