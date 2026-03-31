package br.com.nicomaia.server.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

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
        assertThrows(NoSuchElementException.class, () -> CommandType.valueOf((byte) 0xFF));
    }

    @ParameterizedTest
    @EnumSource(value = CommandType.class, names = {"CONNECT", "BIND", "UDP_ASSOCIATE"})
    void shouldRoundTripByteToEnum(CommandType type) {
        assertEquals(type, CommandType.valueOf(type.getNumber()));
    }
}
