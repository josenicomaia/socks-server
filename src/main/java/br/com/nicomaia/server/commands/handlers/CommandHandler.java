package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.net.Socket;

public interface CommandHandler {
    /**
     * Handles a command using traditional blocking I/O
     * 
     * @param clientSocket the client socket
     * @param command the command to handle
     */
    void handle(Socket clientSocket, Command command);

    /**
     * Handles a command using reactive programming
     * 
     * @param clientConnection the client connection
     * @param command the command to handle
     * @return a Mono that completes when the handling is done
     */
    default Mono<Void> handleReactive(Connection clientConnection, Command command) {
        // Default implementation for backward compatibility
        return Mono.empty();
    }
}
