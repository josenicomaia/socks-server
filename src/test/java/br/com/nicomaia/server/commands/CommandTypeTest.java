package br.com.nicomaia.server.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CommandTypeTest {

  @Test
  void shouldResolveConnect() {
    assertEquals(CommandType.CONNECT, CommandType.valueOf((byte) 0x01));
  }

  @Test
  void shouldResolveBind() {
    assertEquals(CommandType.BIND, CommandType.valueOf((byte) 0x02));
  }

  @Test
  void shouldResolveUdpAssociate() {
    assertEquals(CommandType.UDP_ASSOCIATE, CommandType.valueOf((byte) 0x03));
  }

  @Test
  void shouldThrowForUnknownCommandType() {
    assertThrows(IllegalArgumentException.class, () -> CommandType.valueOf((byte) 0xFF));
  }

  @ParameterizedTest
  @EnumSource(
      value = CommandType.class,
      names = {"CONNECT", "BIND", "UDP_ASSOCIATE"})
  void shouldRoundTripByteToEnum(CommandType type) {
    assertEquals(type, CommandType.valueOf(type.getNumber()));
  }
}
