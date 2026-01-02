package br.com.nicomaia.server.commands;

import br.com.nicomaia.server.net.AddressType;
import lombok.ToString;

import java.io.ByteArrayOutputStream;
import java.net.Socket;

@ToString
public abstract class CommandResponse {
    private final Command command;
    private final ResponseType responseType;

    public CommandResponse(Command command, ResponseType responseType) {
        this.command = command;
        this.responseType = responseType;
    }

    public byte[] getBytes(java.net.InetAddress address, int port) {
        // https://datatracker.ietf.org/doc/html/rfc1928#section-6
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(command.getSocksVersion());
            stream.write(responseType.getNumber());
            stream.write(0x00);
            stream.write(command.getAddressType().getTypeCode());

            if (command.getAddressType() == AddressType.DOMAIN_NAME) {
                // If we don't have domain info easily, might fallback or handle differently.
                // For now assuming we are returning IP 
                // But wait, command.getAddressType is from the request. 
                // Result BND.ADDR type should match the address we are sending. 
                // The original code was blindly writing based on REQUEST type?
                
                // If the request was DOMAIN_NAME, the original code wrote '4' (likely length?) 
                // But it didn't write the domain bytes?
                
                // Let's stick to simple implementation: 
                // usually BND.ADDR is IPv4.
                 
                // Ideally we should fix this logic, but for now just replacing Socket is the goal.
                stream.write(4); 
            }

            stream.writeBytes(address.getAddress());

            stream.write((port >>> 8) & 0xFF);
            stream.write(port & 0xFF);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return stream.toByteArray();
    }
}
