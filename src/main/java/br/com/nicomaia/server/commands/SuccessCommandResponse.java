package br.com.nicomaia.server.commands;

public class SuccessCommandResponse extends CommandResponse {
    public SuccessCommandResponse(Command command) {
        super(command, ResponseType.SUCCEEDED);
    }
}
