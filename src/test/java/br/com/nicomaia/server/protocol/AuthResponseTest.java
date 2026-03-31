package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthResponseTest {

  @Test
  void shouldStoreFields() {
    var response = new AuthResponse((byte) 0x05, SupportedAuthType.NO_AUTH);

    assertEquals((byte) 0x05, response.socksVersion());
    assertEquals(SupportedAuthType.NO_AUTH, response.chosenAuthMethod());
  }

  @Test
  void shouldBuildCorrectResponse() {
    var response = new AuthResponse((byte) 0x05, SupportedAuthType.NO_AUTH);
    byte[] bytes = response.toBytes();

    assertEquals(2, bytes.length);
    assertEquals((byte) 0x05, bytes[0]);
    assertEquals((byte) 0x00, bytes[1]);
  }

  @Test
  void shouldBuildResponseWithUsernameAuth() {
    var response = new AuthResponse((byte) 0x05, SupportedAuthType.USERNAME);
    byte[] bytes = response.toBytes();

    assertEquals(2, bytes.length);
    assertEquals((byte) 0x05, bytes[0]);
    assertEquals((byte) 0x02, bytes[1]);
  }

  @Test
  void shouldProduceReadableToString() {
    var response = new AuthResponse((byte) 0x05, SupportedAuthType.NO_AUTH);
    String str = response.toString();

    assertTrue(str.contains("AuthResponse"));
    assertTrue(str.contains("socksVersion=5"));
    assertTrue(str.contains("NO_AUTH"));
  }
}
