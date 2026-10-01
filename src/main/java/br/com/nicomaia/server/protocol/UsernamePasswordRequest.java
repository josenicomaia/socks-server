package br.com.nicomaia.server.protocol;

import java.io.IOException;
import java.io.InputStream;

/**
 * RFC 1929 username/password sub-negotiation request sent by the client after {@code USERNAME}
 * auth method is selected.
 */
public record UsernamePasswordRequest(byte version, byte[] username, byte[] password) {

  public UsernamePasswordRequest {
    username = username.clone();
    password = password.clone();
  }

  /** Parses {@code VER | ULEN | UNAME | PLEN | PASSWD} from the client stream. */
  public static UsernamePasswordRequest readFrom(InputStream in) throws IOException {
    byte version = SocketReader.readFully(in, 1)[0];
    int usernameLength = SocketReader.readFully(in, 1)[0] & 0xFF;
    byte[] username = SocketReader.readFully(in, usernameLength);
    int passwordLength = SocketReader.readFully(in, 1)[0] & 0xFF;
    byte[] password = SocketReader.readFully(in, passwordLength);

    return new UsernamePasswordRequest(version, username, password);
  }

  @Override
  public byte[] username() {
    return username.clone();
  }

  @Override
  public byte[] password() {
    return password.clone();
  }

  @Override
  public String toString() {
    return "UsernamePasswordRequest{version=" + version + ", credentials=<redacted>}";
  }
}
