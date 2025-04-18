package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;

import java.net.Socket;

public interface CommandHandler {
    // Legacy method for backward compatibility
    void handle(Socket clientSocket, Command command);

}
