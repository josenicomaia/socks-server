package br.com.nicomaia.server.net;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.nicomaia.server.net.resolvers.InetResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AddressResolverTest {

  @Test
  void shouldResolveIPv4Address() throws Exception {
    InetAddress expected = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
    InetResolver ipResolver = mock(InetResolver.class);
    Address address = new Address(new byte[] {10, 0, 0, 1}, AddressType.IPV4);

    when(ipResolver.resolve(address)).thenReturn(expected);

    Map<AddressType, InetResolver> resolvers = Map.of(AddressType.IPV4, ipResolver);
    AddressResolver resolver = new AddressResolver(resolvers);

    InetAddress result = resolver.resolve(address);

    assertEquals(expected, result);
    verify(ipResolver).resolve(address);
  }

  @Test
  void shouldResolveIPv6Address() throws Exception {
    byte[] ipv6Bytes = new byte[16];
    ipv6Bytes[15] = 1;
    InetAddress expected = InetAddress.getByAddress(ipv6Bytes);
    InetResolver ipResolver = mock(InetResolver.class);
    Address address = new Address(ipv6Bytes, AddressType.IPV6);

    when(ipResolver.resolve(address)).thenReturn(expected);

    Map<AddressType, InetResolver> resolvers = Map.of(AddressType.IPV6, ipResolver);
    AddressResolver resolver = new AddressResolver(resolvers);

    InetAddress result = resolver.resolve(address);

    assertEquals(expected, result);
  }

  @Test
  void shouldResolveDomainAddress() throws Exception {
    InetAddress expected = InetAddress.getByAddress(new byte[] {93, 0, 0, 1});
    InetResolver domainResolver = mock(InetResolver.class);
    Address address = new Address("example.com".getBytes(), AddressType.DOMAIN_NAME);

    when(domainResolver.resolve(address)).thenReturn(expected);

    Map<AddressType, InetResolver> resolvers = Map.of(AddressType.DOMAIN_NAME, domainResolver);
    AddressResolver resolver = new AddressResolver(resolvers);

    InetAddress result = resolver.resolve(address);

    assertEquals(expected, result);
  }

  @Test
  void shouldThrowResolverNotFoundForUnregisteredType() {
    Map<AddressType, InetResolver> resolvers = new HashMap<>();
    AddressResolver resolver = new AddressResolver(resolvers);
    Address address = new Address(new byte[] {127, 0, 0, 1}, AddressType.IPV4);

    assertThrows(ResolverNotFoundException.class, () -> resolver.resolve(address));
  }

  @Test
  void shouldPropagateUnknownHostException() throws Exception {
    InetResolver resolver = mock(InetResolver.class);
    Address address = new Address("invalid.host".getBytes(), AddressType.DOMAIN_NAME);

    when(resolver.resolve(address)).thenThrow(new UnknownHostException("not found"));

    Map<AddressType, InetResolver> resolvers = Map.of(AddressType.DOMAIN_NAME, resolver);
    AddressResolver addressResolver = new AddressResolver(resolvers);

    assertThrows(UnknownHostException.class, () -> addressResolver.resolve(address));
  }
}
