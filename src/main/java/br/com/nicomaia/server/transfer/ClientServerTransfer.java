package br.com.nicomaia.server.transfer;

import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientServerTransfer {
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private Thread clientToServerThread;
    private Thread serverToClientThread;

    public ClientServerTransfer(Socket client, Socket server) throws IOException {
        // Set socket options for better data transfer
        client.setTcpNoDelay(true);
        server.setTcpNoDelay(true);

        // Set a reasonable timeout to avoid blocking indefinitely
        client.setSoTimeout(100);
        server.setSoTimeout(100);

        prepareTransfers(client, server);
    }

    private void prepareTransfers(Socket client, Socket server) {
        clientToServerThread = new Thread(() -> {
            try {
                while (client.isConnected()) {
                    transferTo(client.getInputStream(), server.getOutputStream());
                    Thread.sleep(10); // Reduced sleep time for more responsive data transfer
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();

                try {
                    client.close();
                    server.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }, client + " => " + server);

        serverToClientThread = new Thread(() -> {
            try {
                while (server.isConnected()) {
                    transferTo(server.getInputStream(), client.getOutputStream());
                    Thread.sleep(10); // Reduced sleep time for more responsive data transfer
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();

                try {
                    client.close();
                    server.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }, client + " <= " + server);
    }

    private void transferTo(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;

        try {
            // Try to read even if available() returns 0, as it might be blocking
            read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE);
            if (read > 0) {
                out.write(buffer, 0, read);
                out.flush();

                // Try to read more data until we get a timeout or end of stream
                try {
                    while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) > 0) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // This is expected - it means we've read all available data for now
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            // No data available at the moment, which is fine
        }
    }

    public void start() {
        serverToClientThread.start();
        clientToServerThread.start();
    }

    // New reactive methods
    public static Mono<Void> transferReactive(Connection clientConnection, Connection serverConnection) {
        // Forward data from client to server
        Mono<Void> clientToServerTransfer = clientConnection.inbound().receive()
                .doOnNext(buffer -> System.out.println("Client -> Server: " + buffer.readableBytes() + " bytes"))
                .flatMap(buffer -> serverConnection.outbound().send(Mono.just(buffer)))
                .then();

        // Forward data from server to client
        Mono<Void> serverToClientTransfer = serverConnection.inbound().receive()
                .doOnNext(buffer -> System.out.println("Server -> Client: " + buffer.readableBytes() + " bytes"))
                .flatMap(buffer -> clientConnection.outbound().send(Mono.just(buffer)))
                .then();

        // Combine both transfers
        return Mono.when(clientToServerTransfer, serverToClientTransfer);
    }
}
