package br.com.nicomaia.server;

import java.util.Set;

public class LoginNegotiationCommand {
    private final byte socksVersion;
    private final byte availableClientAuthTypes;
    private final Set<SupportedAuthType> supportedAuthTypes;

    public LoginNegotiationCommand(byte socksVersion, byte availableClientAuthTypes, Set<SupportedAuthType> supportedAuthTypes) {
        this.socksVersion = socksVersion;
        this.availableClientAuthTypes = availableClientAuthTypes;
        this.supportedAuthTypes = supportedAuthTypes;
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public byte getAvailableClientAuthTypes() {
        return availableClientAuthTypes;
    }

    public Set<SupportedAuthType> getSupportedAuthTypes() {
        return supportedAuthTypes;
    }

    @Override
    public String toString() {
        return "LoginNegotiationCommand{" +
                "socksVersion=" + socksVersion +
                ", availableClientAuthTypes=" + availableClientAuthTypes +
                ", supportedAuthTypes=" + supportedAuthTypes +
                '}';
    }
}
