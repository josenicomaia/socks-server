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
    // Configurações externalizadas para facilitar ajustes
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 100;
    private static final int DEFAULT_SLEEP_TIME_MS = 10;
    private static final int BUFFER_POOL_SIZE = 16; // Tamanho do pool de buffers

    // Pool de buffers para reduzir alocação de memória
    private static final BlockingQueue<byte[]> bufferPool = new ArrayBlockingQueue<>(BUFFER_POOL_SIZE);
    // Contador para monitorar uso do pool
    private static final AtomicInteger activeBuffers = new AtomicInteger(0);

    private Thread clientToServerThread;
    private Thread serverToClientThread;
    private final int sleepTimeMs;

    static {
        // Inicializa o pool de buffers
        for (int i = 0; i < BUFFER_POOL_SIZE; i++) {
            bufferPool.offer(new byte[DEFAULT_BUFFER_SIZE]);
        }
    }

    public ClientServerTransfer(Socket client, Socket server) throws IOException {
        this(client, server, DEFAULT_SOCKET_TIMEOUT_MS, DEFAULT_SLEEP_TIME_MS);
    }

    // Construtor com timeouts configuráveis
    public ClientServerTransfer(Socket client, Socket server, int socketTimeoutMs, int sleepTimeMs) throws IOException {
        this.sleepTimeMs = sleepTimeMs;

        // Set socket options for better data transfer
        client.setTcpNoDelay(true);
        server.setTcpNoDelay(true);

        // Set configurable timeout
        client.setSoTimeout(socketTimeoutMs);
        server.setSoTimeout(socketTimeoutMs);

        prepareTransfers(client, server);
    }

    private void prepareTransfers(Socket client, Socket server) {
        clientToServerThread = new Thread(() -> {
            try {
                while (client.isConnected()) {
                    transferTo(client.getInputStream(), server.getOutputStream());
                    Thread.sleep(sleepTimeMs); // Usa o tempo de sleep configurável
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
                    Thread.sleep(sleepTimeMs); // Usa o tempo de sleep configurável
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
        // Obtém um buffer do pool com timeout para implementar backpressure
        byte[] buffer = null;
        try {
            buffer = bufferPool.poll(500, TimeUnit.MILLISECONDS);
            if (buffer == null) {
                System.out.println("Buffer pool esgotado, criando novo buffer temporário");
                buffer = new byte[DEFAULT_BUFFER_SIZE]; // Fallback se o pool estiver esgotado
            } else {
                activeBuffers.incrementAndGet();
            }

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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrompido enquanto aguardava buffer disponível");
        } finally {
            // Devolve o buffer ao pool se ele veio do pool
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

    // Enhanced reactive methods with improved logging and error handling
    public static Mono<Void> transferReactive(Connection clientConnection, Connection serverConnection) {
        System.out.println("Starting reactive transfer between connections");

        // Forward data from client to server with improved logging
        Mono<Void> clientToServerTransfer = clientConnection.inbound().receive()
                .doOnNext(buffer -> System.out.println("Client -> Server: " + buffer.readableBytes() + " bytes"))
                .flatMap(buffer -> serverConnection.outbound().send(Mono.just(buffer)))
                .doOnError(e -> System.err.println("Error in client->server transfer: " + e.getMessage()))
                .then();

        // Forward data from server to client with improved logging
        Mono<Void> serverToClientTransfer = serverConnection.inbound().receive()
                .doOnNext(buffer -> System.out.println("Server -> Client: " + buffer.readableBytes() + " bytes"))
                .flatMap(buffer -> clientConnection.outbound().send(Mono.just(buffer)))
                .doOnError(e -> System.err.println("Error in server->client transfer: " + e.getMessage()))
                .then();

        // Combine both transfers and handle completion/errors
        return Mono.when(clientToServerTransfer, serverToClientTransfer)
                .doOnSubscribe(s -> System.out.println("Reactive transfer subscribed"))
                .doOnSuccess(v -> System.out.println("Reactive transfer completed successfully"))
                .doOnError(e -> System.err.println("Reactive transfer failed: " + e.getMessage()))
                .onErrorResume(e -> {
                    // Log the error but don't propagate it to allow graceful shutdown
                    System.err.println("Recovering from transfer error: " + e.getMessage());
                    return Mono.empty();
                });
    }
}
