package br.com.nicomaia.server.net;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum AddressType {
    IPV4((byte) 0x01),
    IPV6((byte) 0x04),
    DOMAIN_NAME((byte) 0x03);

    private final byte typeCode;

    public static AddressType valueOf(byte typeCode) {
        return Arrays.stream(values())
                .filter(commandType -> commandType.typeCode == typeCode)
                .findFirst()
                .get();
    }

    AddressType(byte typeCode) {
        this.typeCode = typeCode;
    }
}
