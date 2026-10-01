package br.com.nicomaia.server.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UsernamePasswordRequestTest {

  private static final byte[] USERNAME_BYTES = "alice".getBytes(StandardCharsets.UTF_8);
  private static final byte[] PASSWORD_BYTES = "s3cret".getBytes(StandardCharsets.UTF_8);

  @Test
  void shouldStoreAllFields() {
    var request = new UsernamePasswordRequest((byte) 0x01, USERNAME_BYTES, PASSWORD_BYTES);

    assertEquals((byte) 0x01, request.version());
    assertArrayEquals(USERNAME_BYTES, request.username());
    assertArrayEquals(PASSWORD_BYTES, request.password());
  }

  @Test
  void shouldDefensivelyCopyConstructorArrays() {
    byte[] username = "alice".getBytes(StandardCharsets.UTF_8);
    byte[] password = "s3cret".getBytes(StandardCharsets.UTF_8);
    var request = new UsernamePasswordRequest((byte) 0x01, username, password);

    username[0] = 'X';
    password[0] = 'X';

    assertEquals('a', request.username()[0]);
    assertEquals('s', request.password()[0]);
  }

  @Test
  void shouldDefensivelyCopyOnAccess() {
    var request = new UsernamePasswordRequest((byte) 0x01, USERNAME_BYTES, PASSWORD_BYTES);

    request.username()[0] = 'X';
    request.password()[0] = 'X';

    assertEquals('a', request.username()[0]);
    assertEquals('s', request.password()[0]);
  }

  @Test
  void shouldReadFromStream() throws IOException {
    var payload = new ByteArrayOutputStream();
    payload.write(0x01);
    payload.write(USERNAME_BYTES.length);
    payload.write(USERNAME_BYTES);
    payload.write(PASSWORD_BYTES.length);
    payload.write(PASSWORD_BYTES);

    var request = UsernamePasswordRequest.readFrom(new ByteArrayInputStream(payload.toByteArray()));

    assertEquals((byte) 0x01, request.version());
    assertArrayEquals(USERNAME_BYTES, request.username());
    assertArrayEquals(PASSWORD_BYTES, request.password());
  }

  @Test
  void shouldThrowEofWhenStreamEndsMidCredentials() throws IOException {
    var payload = new ByteArrayOutputStream();
    payload.write(0x01);
    payload.write(USERNAME_BYTES.length);
    payload.write(USERNAME_BYTES);
    payload.write(PASSWORD_BYTES.length);
    payload.write(PASSWORD_BYTES, 0, 2); // password cut short

    var in = new ByteArrayInputStream(payload.toByteArray());

    assertThrows(EOFException.class, () -> UsernamePasswordRequest.readFrom(in));
  }

  @Test
  void shouldNotLeakCredentialsInToString() {
    var request = new UsernamePasswordRequest((byte) 0x01, USERNAME_BYTES, PASSWORD_BYTES);

    String result = request.toString();

    assertFalse(result.contains("alice"));
    assertFalse(result.contains("s3cret"));
  }
}
