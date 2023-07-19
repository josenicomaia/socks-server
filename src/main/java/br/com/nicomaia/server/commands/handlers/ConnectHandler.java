package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.transfer.ClientServerTransfer;
import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandResponse;
import br.com.nicomaia.server.commands.FailureCommandResponse;
import br.com.nicomaia.server.commands.SuccessCommandResponse;

import java.io.IOException;
import java.net.Socket;

public class ConnectHandler implements CommandHandler {
    public void handle(Socket client, Command command) {
        try {
            Socket proxiedConnection = new Socket(command.getAddress(), command.getPort());
            var response = new SuccessCommandResponse(command, proxiedConnection);

            ClientServerTransfer transfer = new ClientServerTransfer(client, proxiedConnection);
            transfer.start();

            sendResponse(client, response);
        } catch (IOException e) {
            e.printStackTrace();

            try {
                sendResponse(client, new FailureCommandResponse(command));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void sendResponse(Socket client, CommandResponse response) throws IOException {
        System.out.println(response);

        client.getOutputStream().write(response.getBytes(client));
        client.getOutputStream().flush();
    }
}
