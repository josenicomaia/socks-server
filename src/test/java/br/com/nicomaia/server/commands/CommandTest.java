package br.com.nicomaia.server.commands;

import br.com.nicomaia.server.net.AddressType;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    @Test
    void shouldStoreAllFieldsViaRecordAccessors() throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        var command = new Command((byte) 0x05, CommandType.CONNECT, AddressType.IPV4, address, 8080);

        assertEquals((byte) 0x05, command.socksVersion());
        assertEquals(CommandType.CONNECT, command.commandType());
        assertEquals(AddressType.IPV4, command.addressType());
        assertEquals(address, command.address());
        assertEquals(8080, command.port());
    }

    @Test
    void shouldHaveEquality() throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(new byte[]{10, 0, 0, 1});
        var command1 = new Command((byte) 0x05, CommandType.CONNECT, AddressType.IPV4, address, 443);
        var command2 = new Command((byte) 0x05, CommandType.CONNECT, AddressType.IPV4, address, 443);

        assertEquals(command1, command2);
        assertEquals(command1.hashCode(), command2.hashCode());
    }

    @Test
    void shouldProduceToString() throws UnknownHostException {
        InetAddress address = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        var command = new Command((byte) 0x05, CommandType.CONNECT, AddressType.IPV4, address, 80);

        String str = command.toString();
        assertNotNull(str);
        assertTrue(str.contains("CONNECT"));
        assertTrue(str.contains("IPV4"));
    }
}
