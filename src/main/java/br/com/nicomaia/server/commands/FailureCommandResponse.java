package br.com.nicomaia.server.commands;

public class FailureCommandResponse extends CommandResponse {
    public FailureCommandResponse(Command command) {
        super(command, ResponseType.SOCKS_SERVER_FAILURE);
    }
}
