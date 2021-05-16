package br.com.nicomaia.server;

public class LoginNegotiationResult {
    private final byte socksVersion;
    private final SupportedAuthType chosenAuthMethod;

    public LoginNegotiationResult(byte socksVersion, SupportedAuthType chosenAuthMethod) {
        this.socksVersion = socksVersion;
        this.chosenAuthMethod = chosenAuthMethod;
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public SupportedAuthType getChosenAuthMethod() {
        return chosenAuthMethod;
    }

    public byte[] getResponse() {
        return new byte[] {
                socksVersion, chosenAuthMethod.getNumber()
        };
    }
}
