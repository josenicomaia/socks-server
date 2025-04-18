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

    public byte[] getBytes(Socket client) {
        // https://datatracker.ietf.org/doc/html/rfc1928#section-6
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(command.getSocksVersion());
        stream.write(responseType.getNumber());
        stream.write(0x00);
        stream.write(command.getAddressType().getTypeCode());

        if (command.getAddressType() == AddressType.DOMAIN_NAME) {
            stream.write(4);
        }

        // Use the bound address from the command
        stream.writeBytes(command.getAddress().getAddress());

        // Use the bound port from the command
        stream.write((command.getPort() >>> 8) & 0xFF);
        stream.write(command.getPort() & 0xFF);

        return stream.toByteArray();
    }
}
