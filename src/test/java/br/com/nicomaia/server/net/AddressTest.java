package br.com.nicomaia.server.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressTest {

    @Test
    void shouldStoreContentAndType() {
        byte[] content = {127, 0, 0, 1};
        var address = new Address(content, AddressType.IPV4);

        assertArrayEquals(content, address.content());
        assertEquals(AddressType.IPV4, address.addressType());
    }

    @Test
    void shouldStoreDomainAddress() {
        byte[] content = "example.com".getBytes();
        var address = new Address(content, AddressType.DOMAIN_NAME);

        assertEquals("example.com", new String(address.content()));
        assertEquals(AddressType.DOMAIN_NAME, address.addressType());
    }

    @Test
    void shouldStoreIPv6Address() {
        byte[] content = new byte[16];
        content[15] = 1; // ::1
        var address = new Address(content, AddressType.IPV6);

        assertEquals(16, address.content().length);
        assertEquals(AddressType.IPV6, address.addressType());
    }
}
