package br.com.nicomaia.server;

import java.util.Arrays;

public enum AddressType {
    IPV4((byte) 0x01),
    DOMAIN_NAME((byte) 0x03),
    IPV6((byte) 0x04);

    private final byte number;

    AddressType(byte number) {
        this.number = number;
    }

    public byte getNumber() {
        return number;
    }

    public static AddressType valueOf(byte number) {
        return Arrays.stream(values())
                .filter(commandType -> commandType.number == number)
                .findFirst()
                .get();
    }
}
