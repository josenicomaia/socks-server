package br.com.nicomaia.server;

import br.com.nicomaia.server.commands.AddressType;
import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.ConnectHandler;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

public class Main {
    public static void main(String[] args) {
        HandlersHolder handlers = new HandlersHolder();
        handlers.register(CommandType.CONNECT, new ConnectHandler());

        try {
            var serverSocket = new ServerSocket(8089);
            System.out.println(serverSocket);

            while (true) {
                try {
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

                            var command = new Command(socksVersion, commandType, addressType, address, port);
                            System.out.println(command);
                            handlers.get(commandType).handle(clientSocket, command);

                            System.out.printf("Terminating %s...%n", Thread.currentThread());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    clientThread.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
