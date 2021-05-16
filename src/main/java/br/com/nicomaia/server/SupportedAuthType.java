package br.com.nicomaia.server;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum SupportedAuthType {
    NO_AUTH((byte) 0x00),
    USERNAME((byte) 0x02);

    private final byte number;

    SupportedAuthType(byte number) {
        this.number = number;
    }

    public byte getNumber() {
        return number;
    }

    public static Set<SupportedAuthType> valueOf(byte[] buffer) {
        Set<SupportedAuthType> parsedTypes = new HashSet<>();

        Map<Byte, SupportedAuthType> numberIndex = Arrays.stream(values())
                .collect(Collectors.toMap(SupportedAuthType::getNumber, supportedAuthType -> supportedAuthType));

        for (byte number : buffer) {
            SupportedAuthType supportedAuthType = numberIndex.get(number);

            if (supportedAuthType != null) {
                parsedTypes.add(supportedAuthType);
            }
        }

        return parsedTypes;
    }
}
