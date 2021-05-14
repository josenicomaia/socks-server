package br.com.nicomaia.server;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum AuthType {
    NO_AUTH((byte) 0x00),
    GSSAPI((byte) 0x01),
    USER_PASSWORD((byte) 0x02);

    private final byte aByte;

    AuthType(byte aByte) {
        this.aByte = aByte;
    }

    public byte getByte() {
        return aByte;
    }

    public static Set<AuthType> valueOf(byte aByte) {
        return Arrays.stream(values())
                .filter(authType -> (aByte & authType.aByte) == authType.aByte)
                .collect(Collectors.toSet());
    }
}
