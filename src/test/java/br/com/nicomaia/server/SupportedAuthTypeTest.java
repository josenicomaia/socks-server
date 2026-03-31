package br.com.nicomaia.server;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SupportedAuthTypeTest {

    @Test
    void shouldReturnNoAuthForByte0x00() {
        assertEquals((byte) 0x00, SupportedAuthType.NO_AUTH.getNumber());
    }

    @Test
    void shouldReturnUsernameForByte0x02() {
        assertEquals((byte) 0x02, SupportedAuthType.USERNAME.getNumber());
    }

    @Test
    void shouldParseValidAuthTypesFromByteArray() {
        byte[] buffer = {0x00, 0x02};
        Set<SupportedAuthType> result = SupportedAuthType.valueOf(buffer);

        assertEquals(2, result.size());
        assertTrue(result.contains(SupportedAuthType.NO_AUTH));
        assertTrue(result.contains(SupportedAuthType.USERNAME));
    }

    @Test
    void shouldIgnoreUnknownAuthTypes() {
        byte[] buffer = {0x00, 0x05, 0x02};
        Set<SupportedAuthType> result = SupportedAuthType.valueOf(buffer);

        assertEquals(2, result.size());
        assertTrue(result.contains(SupportedAuthType.NO_AUTH));
        assertTrue(result.contains(SupportedAuthType.USERNAME));
    }

    @Test
    void shouldReturnEmptySetWhenNoKnownAuthTypes() {
        byte[] buffer = {0x05, 0x06};
        Set<SupportedAuthType> result = SupportedAuthType.valueOf(buffer);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptySetForEmptyBuffer() {
        byte[] buffer = {};
        Set<SupportedAuthType> result = SupportedAuthType.valueOf(buffer);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleDuplicateAuthTypesInBuffer() {
        byte[] buffer = {0x00, 0x00, 0x02};
        Set<SupportedAuthType> result = SupportedAuthType.valueOf(buffer);

        assertEquals(2, result.size());
    }
}
