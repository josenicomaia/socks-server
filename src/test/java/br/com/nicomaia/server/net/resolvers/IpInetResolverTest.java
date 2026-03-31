package br.com.nicomaia.server.net.resolvers;

import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressType;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class IpInetResolverTest {

    private final IpInetResolver resolver = new IpInetResolver();

    @Test
    void shouldResolveIPv4Address() throws Exception {
        byte[] ipBytes = {127, 0, 0, 1};
        Address address = new Address(ipBytes, AddressType.IPV4);

        InetAddress result = resolver.resolve(address);

        assertArrayEquals(ipBytes, result.getAddress());
    }

    @Test
    void shouldResolveIPv6Address() throws Exception {
        byte[] ipv6Bytes = new byte[16];
        ipv6Bytes[15] = 1; // ::1
        Address address = new Address(ipv6Bytes, AddressType.IPV6);

        InetAddress result = resolver.resolve(address);

        assertArrayEquals(ipv6Bytes, result.getAddress());
    }

    @Test
    void shouldResolvePrivateNetworkAddress() throws Exception {
        byte[] ipBytes = {10, 0, 0, 1};
        Address address = new Address(ipBytes, AddressType.IPV4);

        InetAddress result = resolver.resolve(address);

        assertEquals("/10.0.0.1", result.toString());
    }
}
