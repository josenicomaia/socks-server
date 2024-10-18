package br.com.nicomaia.server.net.resolvers;

import br.com.nicomaia.server.net.Address;

import java.net.InetAddress;
import java.net.UnknownHostException;

public interface InetResolver {
    InetAddress resolve(Address address) throws UnknownHostException;
}
