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

            ClientServerTransfer transfer = new ClientServerTransfer(client, proxiedConnection, 200, 5);

            sendResponse(client, response);

            transfer.start();
        } catch (IOException e) {
            e.printStackTrace();

            try {
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

        TcpClient tcpClient = TcpClient.create()
                .host(command.getAddress().getHostAddress())
                .port(command.getPort());

        return tcpClient.connect()
                .flatMap(serverConnection -> {
                    System.out.println("Connected to target server reactively");

                    var response = new SuccessCommandResponse(command);
                    byte[] responseBytes = response.getBytes();
                    ByteBuf responseBuf = Unpooled.wrappedBuffer(responseBytes);

                    return clientConnection.outbound().send(Mono.just(responseBuf))
                            .then()
                            .thenReturn(serverConnection);
                })
                .flatMap(serverConnection -> 
                    ClientServerTransfer.transferReactive(clientConnection, serverConnection)
                )
                .onErrorResume(e -> {
                    System.err.println("Error in reactive connection: " + e.getMessage());

                    var response = new FailureCommandResponse(command);
                    byte[] responseBytes = response.getBytes();
                    ByteBuf responseBuf = Unpooled.wrappedBuffer(responseBytes);

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
