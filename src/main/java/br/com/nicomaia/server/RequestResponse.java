package br.com.nicomaia.server;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;

public class RequestResponse {
    private final byte socksVersion;
    private final RequestResponseType responseType;
    private final AddressType addressType;
    private final InetAddress address;
    private final int port;

    public RequestResponse(byte socksVersion, RequestResponseType responseType, AddressType addressType, InetAddress address, int port) {
        this.socksVersion = socksVersion;
        this.responseType = responseType;
        this.addressType = addressType;
        this.address = address;
        this.port = port;
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public RequestResponseType getResponseType() {
        return responseType;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public byte[] getResponse() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(socksVersion);
        stream.write(responseType.getNumber());
        stream.write(0x00);
        stream.write(addressType.getNumber());
        stream.writeBytes(address.getAddress());
        stream.write((port >>> 8) & 0xFF);
        stream.write(port & 0xFF);

        return stream.toByteArray();
    }

    @Override
    public String toString() {
        return "RequestResponse{" +
                "socksVersion=" + socksVersion +
                ", responseType=" + responseType +
                ", addressType=" + addressType +
                ", address=" + address +
                ", port=" + port +
                '}';
    }
}
