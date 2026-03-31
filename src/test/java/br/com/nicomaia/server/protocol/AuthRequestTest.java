package br.com.nicomaia.server.protocol;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthRequestTest {

    @Test
    void shouldStoreAllFields() {
        Set<SupportedAuthType> authTypes = Set.of(SupportedAuthType.NO_AUTH);
        var request = new AuthRequest((byte) 0x05, (byte) 0x01, authTypes);

        assertEquals((byte) 0x05, request.socksVersion());
        assertEquals((byte) 0x01, request.availableClientAuthTypes());
        assertEquals(authTypes, request.supportedAuthTypes());
    }

    @Test
    void shouldProduceReadableToString() {
        Set<SupportedAuthType> authTypes = Set.of(SupportedAuthType.NO_AUTH, SupportedAuthType.USERNAME);
        var request = new AuthRequest((byte) 0x05, (byte) 0x02, authTypes);

        String result = request.toString();
        assertTrue(result.contains("AuthRequest"));
        assertTrue(result.contains("socksVersion=5"));
    }
}
