package br.com.nicomaia.server.commands;

import java.net.Socket;

public class SuccessCommandResponse extends CommandResponse {
    private final Socket socket;

    public SuccessCommandResponse(Command command, Socket socket) {
        super(command, ResponseType.SUCCEEDED);
        this.socket = socket;
    }
}
