package br.com.nicomaia.server.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Socks5CredentialsTest {

  @Test
  void shouldCreateCredentialsFromValidUsernameAndPassword() {
    var credentials = Socks5Credentials.of("alice", "s3cret");

    assertTrue(credentials.matches(bytes("alice"), bytes("s3cret")));
  }

  @Test
  void shouldRejectMismatchedUsername() {
    var credentials = Socks5Credentials.of("alice", "s3cret");

    assertFalse(credentials.matches(bytes("bob"), bytes("s3cret")));
  }

  @Test
  void shouldRejectMismatchedPassword() {
    var credentials = Socks5Credentials.of("alice", "s3cret");

    assertFalse(credentials.matches(bytes("alice"), bytes("wrong")));
  }

  @Test
  void shouldRejectWhenBothFieldsMismatch() {
    var credentials = Socks5Credentials.of("alice", "s3cret");

    assertFalse(credentials.matches(bytes("bob"), bytes("wrong")));
  }

  @Test
  void shouldThrowWhenUsernameIsMissing() {
    var exception =
        assertThrows(IllegalStateException.class, () -> Socks5Credentials.of(null, "s3cret"));

    assertTrue(exception.getMessage().contains("Username"));
  }

  @Test
  void shouldThrowWhenUsernameIsBlank() {
    assertThrows(IllegalStateException.class, () -> Socks5Credentials.of("   ", "s3cret"));
  }

  @Test
  void shouldThrowWhenPasswordIsMissing() {
    var exception =
        assertThrows(IllegalStateException.class, () -> Socks5Credentials.of("alice", null));

    assertTrue(exception.getMessage().contains("Password"));
  }

  @Test
  void shouldThrowWhenPasswordIsBlank() {
    assertThrows(IllegalStateException.class, () -> Socks5Credentials.of("alice", "   "));
  }

  @Test
  void shouldAcceptCredentialsAtTheRfc1929LengthLimit() {
    String longest = "u".repeat(Socks5Credentials.MAX_LENGTH_BYTES);

    var credentials = Socks5Credentials.of(longest, longest);

    assertTrue(credentials.matches(bytes(longest), bytes(longest)));
  }

  @Test
  void shouldThrowWhenUsernameExceedsRfc1929Length() {
    String tooLong = "u".repeat(Socks5Credentials.MAX_LENGTH_BYTES + 1);

    var exception =
        assertThrows(IllegalStateException.class, () -> Socks5Credentials.of(tooLong, "s3cret"));

    assertTrue(exception.getMessage().contains("255"));
  }

  @Test
  void shouldThrowWhenPasswordExceedsRfc1929LengthInUtf8Bytes() {
    // 128 characters, but 256 bytes in UTF-8: the limit is on encoded bytes, not characters.
    String tooLong = "é".repeat(128);

    assertThrows(IllegalStateException.class, () -> Socks5Credentials.of("alice", tooLong));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
