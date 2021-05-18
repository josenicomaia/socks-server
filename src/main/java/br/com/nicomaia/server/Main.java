package br.com.nicomaia.server;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static Map<String, Integer> schemaWithPort;

    static {
        schemaWithPort = new HashMap<>() {{
            put("http", 80);
            put("https", 443);
        }};
    }

    public static void main(String[] args) {
        try {
            var serverSocket = new ServerSocket(8089, 50, InetAddress.getLoopbackAddress());
            System.out.println(serverSocket);

            try (var clientSocket = serverSocket.accept()) {
                System.out.println(clientSocket);

                byte[] buffer = new byte[2];
                clientSocket.getInputStream().read(buffer);
                System.out.println("Q: " + Arrays.toString(buffer));

                byte socksVersion = buffer[0];
                byte availableClientAuthTypes = buffer[1];

                buffer = new byte[availableClientAuthTypes];
                clientSocket.getInputStream().read(buffer);
                System.out.println("Q: " + Arrays.toString(buffer));

                var loginNegotiationCommand = new LoginNegotiationCommand(socksVersion, availableClientAuthTypes, SupportedAuthType.valueOf(buffer));
                var loginNegotiationResult = new LoginNegotiationResult(socksVersion, SupportedAuthType.NO_AUTH);
                clientSocket.getOutputStream().write(loginNegotiationResult.getResponse());
                System.out.println("R: " + Arrays.toString(loginNegotiationResult.getResponse()));

                buffer = new byte[4];
                clientSocket.getInputStream().read(buffer);
                System.out.println("Q: " + Arrays.toString(buffer));

                socksVersion = buffer[0];
                CommandType commandType = CommandType.valueOf(buffer[1]);
                AddressType addressType = AddressType.valueOf(buffer[3]);
                InetAddress address = null;

                if (addressType == AddressType.IPV6) {
                    buffer = new byte[16];
                } else if (addressType == AddressType.IPV4) {
                    buffer = new byte[4];
                }

                clientSocket.getInputStream().read(buffer);
                address = InetAddress.getByAddress(buffer);

                // byte = 1 byte (8 bits - 1 bit for signaling)
                // short = 2 bytes (16 bits - 1 bit for signaling)
                // int = 4 bytes (32 bits - 1 bit for signaling)
                // long = 8 bytes (64 bits - 1 bit for signaling)

                buffer = new byte[2];
                clientSocket.getInputStream().read(buffer);
                // Converting unsigned byte to signed and then concatenate the numbers
                int port = ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);

                clientSocket.getOutputStream().write(new RequestHandler().handle(new RequestCommand(socksVersion, commandType, addressType, address, port)).getResponse());
                clientSocket.getOutputStream().flush();

                Socket proxiedConnection = Session.getInstance().get("connection");

                int read;
//                byte[] bytes = new byte[proxiedConnection.getSendBufferSize()];
                byte[] bytes = new byte[8192];

                // @TODO: Don't know why but without this it DOESN'T works
                System.out.println(clientSocket.getInputStream().available());

                while (clientSocket.getInputStream().available() > 0) {
                    read = clientSocket.getInputStream().read(bytes, 0, 8192);
                    System.out.println(read);
                    proxiedConnection.getOutputStream().write(bytes, 0, read);
                }

                Thread.sleep(1000);

                while (proxiedConnection.getInputStream().available() > 0) {
                    read = proxiedConnection.getInputStream().read(bytes, 0, 8192);
                    System.out.println(read);
                    clientSocket.getOutputStream().write(bytes, 0, read);
                }

                proxiedConnection.close();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static int getPort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }

        return schemaWithPort.getOrDefault(uri.getScheme().toLowerCase(), -1);
    }
}
