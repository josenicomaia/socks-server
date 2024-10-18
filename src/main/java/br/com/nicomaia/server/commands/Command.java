package br.com.nicomaia.server.commands;

import br.com.nicomaia.server.net.AddressType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.net.InetAddress;

@AllArgsConstructor
@Data
@ToString
public class Command {
    private final byte socksVersion;
    private final CommandType commandType;
    private final AddressType addressType;
    private final InetAddress address;
    private final int port;
}
