package br.com.nicomaia.server;

import java.net.InetAddress;

public class Command {
    private final byte socksVersion;
    private final CommandType commandType;
    private final AddressType addressType;
    private final InetAddress address;
    private final int port;

    public Command(byte socksVersion, CommandType commandType, AddressType addressType, InetAddress address, int port) {
        this.socksVersion = socksVersion;
        this.commandType = commandType;
        this.addressType = addressType;
        this.address = address;
        this.port = port;
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public AddressType getAddressType() {
        return addressType;
    }


    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "Command{" +
                "socksVersion=" + socksVersion +
                ", commandType=" + commandType +
                ", addressType=" + addressType +
                ", address='" + address + '\'' +
                ", port=" + port +
                '}';
    }
}
