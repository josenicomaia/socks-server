package br.com.nicomaia.server.protocol;

/** RFC 1929 username/password sub-negotiation response sent back to the client. */
public record UsernamePasswordResponse(byte version, boolean success) {

  /** The sub-negotiation version defined by RFC 1929 — always 0x01, regardless of client input. */
  public static final byte VERSION = 0x01;

  private static final byte STATUS_SUCCESS = 0x00;
  private static final byte STATUS_FAILURE = 0x01;

  public static UsernamePasswordResponse forOutcome(boolean success) {
    return new UsernamePasswordResponse(VERSION, success);
  }

  public byte[] toBytes() {
    return new byte[] {version, success ? STATUS_SUCCESS : STATUS_FAILURE};
  }
}
