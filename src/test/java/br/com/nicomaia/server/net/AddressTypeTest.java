package br.com.nicomaia.server.net;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class AddressTypeTest {

    @Test
    void shouldResolveIPv4() {
        assertEquals(AddressType.IPV4, AddressType.valueOf((byte) 0x01));
    }

    @Test
    void shouldResolveDomainName() {
        assertEquals(AddressType.DOMAIN_NAME, AddressType.valueOf((byte) 0x03));
    }

    @Test
    void shouldResolveIPv6() {
        assertEquals(AddressType.IPV6, AddressType.valueOf((byte) 0x04));
    }

    @Test
    void shouldThrowForUnknownAddressType() {
        assertThrows(IllegalArgumentException.class, () -> AddressType.valueOf((byte) 0xFF));
    }

    @Test
    void shouldReturnCorrectTypeCodes() {
        assertEquals((byte) 0x01, AddressType.IPV4.getTypeCode());
        assertEquals((byte) 0x03, AddressType.DOMAIN_NAME.getTypeCode());
        assertEquals((byte) 0x04, AddressType.IPV6.getTypeCode());
    }

    @Test
    void shouldRoundTripAllValues() {
        for (AddressType type : AddressType.values()) {
            assertEquals(type, AddressType.valueOf(type.getTypeCode()));
        }
    }
}
