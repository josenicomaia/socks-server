package br.com.nicomaia.server.net.resolvers;

import br.com.nicomaia.server.net.Address;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class DomainInetResolver implements InetResolver {
    public InetAddress resolve(Address address) throws UnknownHostException {
        return InetAddress.getByName(new String(address.content()));
    }
}
