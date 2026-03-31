package br.com.nicomaia.server.connection;

import br.com.nicomaia.server.config.ServerConfig;
import br.com.nicomaia.server.protocol.SocksProtocolHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionHandler {

    private static final Logger logger = Logger.getLogger(ConnectionHandler.class.getName());

    private final ServerConfig config;
    private final SocksProtocolHandler protocolHandler;

    public ConnectionHandler(ServerConfig config) {
        this.config = config;
        this.protocolHandler = new SocksProtocolHandler(config.addressResolver(), config.handlers());
    }

    public void start() {
        try (var serverSocket = new ServerSocket(config.port())) {
            logger.info("SOCKS server listening on port " + config.port());

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    Thread.ofVirtual()
                            .name("client-handler-", clientSocket.hashCode())
                            .start(() -> {
                                logger.fine(() -> "Starting " + Thread.currentThread());
                                logger.fine(() -> clientSocket.toString());

                                protocolHandler.handle(clientSocket);

                                logger.fine(() -> "Terminating " + Thread.currentThread());
                            });
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Error accepting connection", e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start server", e);
        }
    }
}
