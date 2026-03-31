package br.com.nicomaia.server.commands;

import br.com.nicomaia.server.net.AddressType;

import java.net.InetAddress;

public record Command(byte socksVersion, CommandType commandType, AddressType addressType, InetAddress address, int port) {
}
