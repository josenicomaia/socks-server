package br.com.nicomaia.server.protocol;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum SupportedAuthType {
  NO_AUTH((byte) 0x00),
  USERNAME((byte) 0x02),
  /** Server reply meaning "none of the offered methods is acceptable" — never a client offer. */
  NO_ACCEPTABLE_METHODS((byte) 0xFF);

  private static final Map<Byte, SupportedAuthType> OFFERABLE_BY_NUMBER =
      Arrays.stream(values())
          .filter(type -> type != NO_ACCEPTABLE_METHODS)
          .collect(Collectors.toMap(SupportedAuthType::getNumber, Function.identity()));

  private final byte number;

  SupportedAuthType(byte number) {
    this.number = number;
  }

  public byte getNumber() {
    return number;
  }

  /**
   * Parses the methods offered by a client. Unknown method numbers are ignored, and so is {@code
   * 0xFF}, which RFC 1928 reserves for the server's rejection reply.
   */
  public static Set<SupportedAuthType> valueOf(byte[] buffer) {
    Set<SupportedAuthType> parsedTypes = EnumSet.noneOf(SupportedAuthType.class);

    for (byte number : buffer) {
      SupportedAuthType supportedAuthType = OFFERABLE_BY_NUMBER.get(number);

      if (supportedAuthType != null) {
        parsedTypes.add(supportedAuthType);
      }
    }

    return parsedTypes;
  }
}
