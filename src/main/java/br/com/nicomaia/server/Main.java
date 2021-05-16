package br.com.nicomaia.server;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
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

            try (var socket = serverSocket.accept()) {
                System.out.println(socket);

                byte[] buffer = new byte[2];
                socket.getInputStream().read(buffer);
                System.out.println("Q: " + Arrays.toString(buffer));

                byte socksVersion = buffer[0];
                byte availableClientAuthTypes = buffer[1];

                buffer = new byte[availableClientAuthTypes];
                socket.getInputStream().read(buffer);
                System.out.println("Q: " + Arrays.toString(buffer));

                var loginNegotiationCommand = new LoginNegotiationCommand(socksVersion, availableClientAuthTypes, SupportedAuthType.valueOf(buffer));
                var loginNegotiationResult = new LoginNegotiationResult(socksVersion, SupportedAuthType.NO_AUTH);
                socket.getOutputStream().write(loginNegotiationResult.getResponse());
                System.out.println("R: " + Arrays.toString(loginNegotiationResult.getResponse()));

                buffer = new byte[4];
                socket.getInputStream().read(buffer);
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

                socket.getInputStream().read(buffer);
                address = InetAddress.getByAddress(buffer);

                buffer = new byte[2];
                socket.getInputStream().read(buffer);
                // Converting unsigned byte to signed and then concatenate the numbers
//                int port = ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
                int port = ByteBuffer.wrap(buffer).getShort() & 0xFFFF;

                Command command = new Command(socksVersion, commandType, addressType, address, port);
                System.out.println(command);


//                byte chosenAuthMethod = (byte) 0xFF; // none

//                int bytesRead = socket.getInputStream().read(version, 0, 1);
//                System.out.println(Arrays.toString(version));
//
//                try (var inputStreamReader = new InputStreamReader(socket.getInputStream())) {
//                    try (var bufferedReader = new BufferedReader(inputStreamReader)) {
//                        String httpQuery = bufferedReader.readLine();
//                        String[] requestParts = httpQuery.split(" ", 3);
//                        URI uri = URI.create(requestParts[1]);
//                        System.out.println(uri.getHost() + " " + getPort(uri));
//
//                        try (var forwardSocket = new Socket(uri.getHost(), getPort(uri))) {
////                            socket.getInputStream().transferTo(forwardSocket.getOutputStream());
//                            var printWriter = new PrintWriter(forwardSocket.getOutputStream());
//                            bufferedReader.transferTo(printWriter);
//                        }
//                    }
//                }
            }
        } catch (IOException e) {
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
