package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import java.net.Socket;

public interface CommandHandler {
    void handle(Socket clientSocket, Command command);

    default Mono<Void> handleReactive(Connection clientConnection, Command command) {
        return Mono.empty();
    }
}
