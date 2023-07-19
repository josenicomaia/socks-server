package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;

import java.net.Socket;

public interface CommandHandler {
    void handle(Socket clientSocket, Command command);
}
