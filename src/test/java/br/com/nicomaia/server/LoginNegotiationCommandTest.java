package br.com.nicomaia.server;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginNegotiationCommandTest {

    @Test
    void shouldStoreAllFields() {
        Set<SupportedAuthType> authTypes = Set.of(SupportedAuthType.NO_AUTH);
        var command = new LoginNegotiationCommand((byte) 0x05, (byte) 0x01, authTypes);

        assertEquals((byte) 0x05, command.getSocksVersion());
        assertEquals((byte) 0x01, command.getAvailableClientAuthTypes());
        assertEquals(authTypes, command.getSupportedAuthTypes());
    }

    @Test
    void shouldProduceReadableToString() {
        Set<SupportedAuthType> authTypes = Set.of(SupportedAuthType.NO_AUTH, SupportedAuthType.USERNAME);
        var command = new LoginNegotiationCommand((byte) 0x05, (byte) 0x02, authTypes);

        String result = command.toString();
        assertTrue(result.contains("LoginNegotiationCommand"));
        assertTrue(result.contains("socksVersion=5"));
    }
}
