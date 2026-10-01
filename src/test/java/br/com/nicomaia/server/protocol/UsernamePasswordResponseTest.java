package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UsernamePasswordResponseTest {

  @Test
  void shouldEncodeSuccessResponse() {
    var response = new UsernamePasswordResponse((byte) 0x01, true);

    assertArrayEquals(new byte[] {0x01, 0x00}, response.toBytes());
  }

  @Test
  void shouldEncodeFailureResponse() {
    var response = new UsernamePasswordResponse((byte) 0x01, false);

    assertArrayEquals(new byte[] {0x01, 0x01}, response.toBytes());
  }

  @Test
  void forOutcomeShouldAlwaysUseRfc1929Version() {
    assertEquals(
        UsernamePasswordResponse.VERSION, UsernamePasswordResponse.forOutcome(true).version());
    assertEquals(
        UsernamePasswordResponse.VERSION, UsernamePasswordResponse.forOutcome(false).version());
  }

  @Test
  void forOutcomeShouldEncodeSuccessAndFailure() {
    assertArrayEquals(new byte[] {0x01, 0x00}, UsernamePasswordResponse.forOutcome(true).toBytes());
    assertArrayEquals(
        new byte[] {0x01, 0x01}, UsernamePasswordResponse.forOutcome(false).toBytes());
  }
}
