package br.com.nicomaia.server.protocol;

public record AuthResponse(byte socksVersion, SupportedAuthType chosenAuthMethod) {

    public byte[] toBytes() {
        return new byte[]{socksVersion, chosenAuthMethod.getNumber()};
    }
}
