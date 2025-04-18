package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.transfer.ClientServerTransfer;
import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandResponse;
import br.com.nicomaia.server.commands.FailureCommandResponse;
import br.com.nicomaia.server.commands.SuccessCommandResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

import java.io.IOException;
import java.net.Socket;

public class ConnectHandler implements CommandHandler {
    @Override
    public void handle(Socket client, Command command) {
        Socket proxiedConnection = null;
        try {
            proxiedConnection = new Socket(command.getAddress(), command.getPort());
            var response = new SuccessCommandResponse(command);

            // Use the optimized version with configurable timeouts
            ClientServerTransfer transfer = new ClientServerTransfer(client, proxiedConnection, 200, 5);

            // Send response before starting the transfer to ensure the client gets the response
            sendResponse(client, response);

            // Start the transfer after sending the response
            transfer.start();
        } catch (IOException e) {
            e.printStackTrace();

            try {
                // Close the proxied connection if it was created
                if (proxiedConnection != null && !proxiedConnection.isClosed()) {
                    try {
                        proxiedConnection.close();
                    } catch (IOException closeEx) {
                        closeEx.printStackTrace();
                    }
                }

                sendResponse(client, new FailureCommandResponse(command));
            } catch (IOException ex) {
                ex.printStackTrace();

                // Try to close the client socket if everything else failed
                try {
                    if (!client.isClosed()) {
                        client.close();
                    }
                } catch (IOException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }

    @Override
    public Mono<Void> handleReactive(Connection clientConnection, Command command) {
        System.out.println("Handling connection reactively to " + command.getAddress() + ":" + command.getPort());

        // Create a TcpClient to connect to the target server
        TcpClient tcpClient = TcpClient.create()
                .host(command.getAddress().getHostAddress())
                .port(command.getPort());

        // Connect to the target server
        return tcpClient.connect()
                .flatMap(serverConnection -> {
                    System.out.println("Connected to target server reactively");

                    // Create success response
                    var response = new SuccessCommandResponse(command);
                    byte[] responseBytes = response.getBytes();
                    ByteBuf responseBuf = Unpooled.wrappedBuffer(responseBytes);

                    // Send the response to the client
                    return clientConnection.outbound().send(Mono.just(responseBuf))
                            .then()
                            .thenReturn(serverConnection);
                })
                .flatMap(serverConnection -> 
                    // Start the data transfer between client and server
                    ClientServerTransfer.transferReactive(clientConnection, serverConnection)
                )
                .onErrorResume(e -> {
                    System.err.println("Error in reactive connection: " + e.getMessage());

                    // Create failure response
                    var response = new FailureCommandResponse(command);
                    byte[] responseBytes = response.getBytes();
                    ByteBuf responseBuf = Unpooled.wrappedBuffer(responseBytes);

                    // Send the failure response to the client
                    return clientConnection.outbound().send(Mono.just(responseBuf))
                            .then();
                });
    }

    private void sendResponse(Socket client, CommandResponse response) throws IOException {
        System.out.println(response);

        client.getOutputStream().write(response.getBytes());
        client.getOutputStream().flush();
    }
}
