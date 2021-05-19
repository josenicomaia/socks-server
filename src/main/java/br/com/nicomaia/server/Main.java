package br.com.nicomaia.server;

import jdk.swing.interop.SwingInterOpUtils;

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

            while (true) {
                var clientSocket = serverSocket.accept();

                var clientThread = new Thread(() -> {
                    try {
                        System.out.printf("Starting %s...%n", Thread.currentThread());
                        System.out.println(clientSocket);

                        byte[] buffer = new byte[2];
                        clientSocket.getInputStream().read(buffer);

                        byte socksVersion = buffer[0];
                        byte availableClientAuthTypes = buffer[1];

                        buffer = new byte[availableClientAuthTypes];
                        clientSocket.getInputStream().read(buffer);

                        var loginNegotiationCommand = new LoginNegotiationCommand(socksVersion, availableClientAuthTypes, SupportedAuthType.valueOf(buffer));
                        var loginNegotiationResult = new LoginNegotiationResult(socksVersion, SupportedAuthType.NO_AUTH);

                        System.out.println(loginNegotiationCommand);
                        System.out.println(loginNegotiationResult);

                        clientSocket.getOutputStream().write(loginNegotiationResult.getResponse());

                        buffer = new byte[4];
                        clientSocket.getInputStream().read(buffer);

                        socksVersion = buffer[0];
                        CommandType commandType = CommandType.valueOf(buffer[1]);
                        AddressType addressType = AddressType.valueOf(buffer[3]);
                        InetAddress address = null;

                        if (AddressType.IPV6 == addressType) {
                            buffer = new byte[16];
                        } else if (AddressType.IPV4 == addressType) {
                            buffer = new byte[4];
                        } else if (AddressType.DOMAIN_NAME == addressType) {
                            buffer = new byte[1];
                            clientSocket.getInputStream().read(buffer);
                            buffer = new byte[buffer[0]];
                        }

                        clientSocket.getInputStream().read(buffer);

                        if (AddressType.DOMAIN_NAME == addressType) {
                            address = InetAddress.getByName(new String(buffer));
                        } else {
                            address = InetAddress.getByAddress(buffer);
                        }

                        buffer = new byte[2];
                        clientSocket.getInputStream().read(buffer);
                        // Converting unsigned byte to signed and then concatenate the numbers
                        int port = ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);

                        var requestCommand = new RequestCommand(socksVersion, commandType, addressType, address, port);
                        var requestResponse = new RequestHandler().handle(requestCommand);

                        System.out.println(requestCommand);
                        System.out.println(requestResponse);

                        clientSocket.getOutputStream().write(requestResponse.getResponse());
                        clientSocket.getOutputStream().flush();

                        Socket proxiedConnection = Session.getInstance().get("connection");

                        var clientToProxyThread = new Thread(() -> {
                            try {
                                clientSocket.getInputStream().transferTo(proxiedConnection.getOutputStream());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

                        var proxyToClientThread = new Thread(() -> {
                            try {
                                proxiedConnection.getInputStream().transferTo(clientSocket.getOutputStream());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

                        clientToProxyThread.start();
                        proxyToClientThread.start();

                        clientToProxyThread.join();
                        proxyToClientThread.join();

                        System.out.printf("Terminating %s...%n", Thread.currentThread());
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                });

                clientThread.start();
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
