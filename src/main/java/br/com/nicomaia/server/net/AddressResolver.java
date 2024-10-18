package br.com.nicomaia.server.net;

import br.com.nicomaia.server.net.resolvers.InetResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

public class AddressResolver {
    private final Map<AddressType, InetResolver> resolvers;

    public AddressResolver(Map<AddressType, InetResolver> resolvers) {
        this.resolvers = resolvers;
    }

    public InetAddress resolve(Address address) throws UnknownHostException, ResolverNotFoundException {
        InetResolver resolver = resolvers.get(address.addressType());

        if (resolver == null) {
            throw new ResolverNotFoundException();
        }

        return resolver.resolve(address);
    }
}
