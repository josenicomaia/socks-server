package br.com.nicomaia.server.commands;

import java.util.Arrays;

public enum CommandType {
    BIND((byte) 0x02),
    BIND_REPLY((byte) 0x00),
    CONNECT((byte) 0x01),
    UDP_ASSOCIATE((byte) 0x03),
    UDP_REPLY((byte) 0x00);

    private final byte number;

    CommandType(byte number) {
        this.number = number;
    }

    public byte getNumber() {
        return number;
    }

    public static CommandType valueOf(byte number) {
        return Arrays.stream(values())
                .filter(commandType -> commandType.number == number)
                .findFirst()
                .get();
    }
}
