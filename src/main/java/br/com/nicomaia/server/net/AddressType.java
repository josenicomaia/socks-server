package br.com.nicomaia.server.net;

import java.util.Arrays;

public enum AddressType {
    IPV4((byte) 0x01),
    IPV6((byte) 0x04),
    DOMAIN_NAME((byte) 0x03);

    private final byte typeCode;

    AddressType(byte typeCode) {
        this.typeCode = typeCode;
    }

    public byte getTypeCode() {
        return typeCode;
    }

    public static AddressType valueOf(byte typeCode) {
        return Arrays.stream(values())
                .filter(commandType -> commandType.typeCode == typeCode)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown address type: 0x" + String.format("%02X", typeCode)));
    }
}
