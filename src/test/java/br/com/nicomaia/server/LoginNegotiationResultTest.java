package br.com.nicomaia.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginNegotiationResultTest {

    @Test
    void shouldStoreFields() {
        var result = new LoginNegotiationResult((byte) 0x05, SupportedAuthType.NO_AUTH);

        assertEquals((byte) 0x05, result.getSocksVersion());
        assertEquals(SupportedAuthType.NO_AUTH, result.getChosenAuthMethod());
    }

    @Test
    void shouldBuildCorrectResponse() {
        var result = new LoginNegotiationResult((byte) 0x05, SupportedAuthType.NO_AUTH);
        byte[] response = result.getResponse();

        assertEquals(2, response.length);
        assertEquals((byte) 0x05, response[0]);
        assertEquals((byte) 0x00, response[1]);
    }

    @Test
    void shouldBuildResponseWithUsernameAuth() {
        var result = new LoginNegotiationResult((byte) 0x05, SupportedAuthType.USERNAME);
        byte[] response = result.getResponse();

        assertEquals(2, response.length);
        assertEquals((byte) 0x05, response[0]);
        assertEquals((byte) 0x02, response[1]);
    }

    @Test
    void shouldProduceReadableToString() {
        var result = new LoginNegotiationResult((byte) 0x05, SupportedAuthType.NO_AUTH);
        String str = result.toString();

        assertTrue(str.contains("LoginNegotiationResult"));
        assertTrue(str.contains("socksVersion=5"));
        assertTrue(str.contains("NO_AUTH"));
    }
}
