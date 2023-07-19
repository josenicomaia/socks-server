package br.com.nicomaia.server.commands;

import java.util.Arrays;

public enum ResponseType {
    SUCCEEDED((byte) 0x00),
    SOCKS_SERVER_FAILURE((byte) 0x01),
    CONNECTION_NOT_ALLOWED((byte) 0x02),
    NETWORK_UNREACHABLE((byte) 0x03),
    HOST_UNREACHABLE((byte) 0x04),
    CONNECTION_REFUSED((byte) 0x05),
    TTL_EXPIRED((byte) 0x06),
    COMMAND_NOT_SUPPORTED((byte) 0x07),
    ADDRESS_TYPE_NOT_SUPPORTED((byte) 0x08),
    UNASSIGNED((byte) 0x09);

    private final byte number;

    ResponseType(byte number) {
        this.number = number;
    }

    public byte getNumber() {
        return number;
    }

    public static ResponseType valueOf(byte number) {
        return Arrays.stream(values())
                .filter(commandType -> commandType.number == number)
                .findFirst()
                .get();
    }
}
