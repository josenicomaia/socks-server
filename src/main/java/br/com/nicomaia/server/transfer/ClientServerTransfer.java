package br.com.nicomaia.server.transfer;

import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientServerTransfer {
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 100;
    private static final int DEFAULT_SLEEP_TIME_MS = 10;
    private static final int BUFFER_POOL_SIZE = 16;

    private static final BlockingQueue<byte[]> bufferPool = new ArrayBlockingQueue<>(BUFFER_POOL_SIZE);
    private static final AtomicInteger activeBuffers = new AtomicInteger(0);

    private Thread clientToServerThread;
    private Thread serverToClientThread;
    private final int sleepTimeMs;

    static {
        for (int i = 0; i < BUFFER_POOL_SIZE; i++) {
            bufferPool.offer(new byte[DEFAULT_BUFFER_SIZE]);
        }
    }

    public ClientServerTransfer(Socket client, Socket server) throws IOException {
        this(client, server, DEFAULT_SOCKET_TIMEOUT_MS, DEFAULT_SLEEP_TIME_MS);
    }

    public ClientServerTransfer(Socket client, Socket server, int socketTimeoutMs, int sleepTimeMs) throws IOException {
        this.sleepTimeMs = sleepTimeMs;

        client.setTcpNoDelay(true);
        server.setTcpNoDelay(true);

        client.setSoTimeout(socketTimeoutMs);
        server.setSoTimeout(socketTimeoutMs);

        prepareTransfers(client, server);
    }

    private void prepareTransfers(Socket client, Socket server) {
        clientToServerThread = new Thread(() -> {
            try {
                while (client.isConnected()) {
                    transferTo(client.getInputStream(), server.getOutputStream());
                    Thread.sleep(sleepTimeMs);
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
                    Thread.sleep(sleepTimeMs);
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
        byte[] buffer = null;
        try {
            buffer = bufferPool.poll(500, TimeUnit.MILLISECONDS);
            if (buffer == null) {
                System.out.println("Buffer pool esgotado, criando novo buffer temporário");
                buffer = new byte[DEFAULT_BUFFER_SIZE];
            } else {
                activeBuffers.incrementAndGet();
            }

            int read;
            try {
                read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE);
                if (read > 0) {
                    out.write(buffer, 0, read);
                    out.flush();

                    try {
                        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) > 0) {
                            out.write(buffer, 0, read);
                            out.flush();
                        }
                    } catch (java.net.SocketTimeoutException e) {
                    }
                }
            } catch (java.net.SocketTimeoutException e) {
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrompido enquanto aguardava buffer disponível");
        } finally {
            if (buffer != null && activeBuffers.get() > 0) {
                bufferPool.offer(buffer);
                activeBuffers.decrementAndGet();
            }
        }
    }

    public void start() {
        serverToClientThread.start();
        clientToServerThread.start();
    }

    public static Mono<Void> transferReactive(Connection clientConnection, Connection serverConnection) {
        System.out.println("Starting reactive transfer between connections");

        Mono<Void> clientToServerTransfer = clientConnection.inbound().receive()
                .doOnNext(buffer -> System.out.println("Client -> Server: " + buffer.readableBytes() + " bytes"))
                .flatMap(buffer -> serverConnection.outbound().send(Mono.just(buffer)))
                .doOnError(e -> System.err.println("Error in client->server transfer: " + e.getMessage()))
                .then();

        Mono<Void> serverToClientTransfer = serverConnection.inbound().receive()
                .doOnNext(buffer -> System.out.println("Server -> Client: " + buffer.readableBytes() + " bytes"))
                .flatMap(buffer -> clientConnection.outbound().send(Mono.just(buffer)))
                .doOnError(e -> System.err.println("Error in server->client transfer: " + e.getMessage()))
                .then();

        return Mono.when(clientToServerTransfer, serverToClientTransfer)
                .doOnSubscribe(s -> System.out.println("Reactive transfer subscribed"))
                .doOnSuccess(v -> System.out.println("Reactive transfer completed successfully"))
                .doOnError(e -> System.err.println("Reactive transfer failed: " + e.getMessage()))
                .onErrorResume(e -> {
                    System.err.println("Recovering from transfer error: " + e.getMessage());
                    return Mono.empty();
                });
    }
}
