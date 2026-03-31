package br.com.nicomaia.server.protocol;

import java.util.Set;

public record AuthRequest(byte socksVersion, byte availableClientAuthTypes, Set<SupportedAuthType> supportedAuthTypes) {
}
