package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.transfer.ClientServerTransfer;
import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandResponse;
import br.com.nicomaia.server.commands.FailureCommandResponse;
import br.com.nicomaia.server.commands.SuccessCommandResponse;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectHandler implements CommandHandler {

    private static final Logger logger = Logger.getLogger(ConnectHandler.class.getName());

    public void handle(Socket client, Command command) {
        try {
            Socket proxiedConnection = new Socket(command.address(), command.port());
            var response = new SuccessCommandResponse(command, proxiedConnection);

            ClientServerTransfer transfer = new ClientServerTransfer(client, proxiedConnection);
            transfer.start();

            sendResponse(client, response);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Connect failed to " + command.address() + ":" + command.port(), e);

            try {
                sendResponse(client, new FailureCommandResponse(command));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void sendResponse(Socket client, CommandResponse response) throws IOException {
        logger.info(response.toString());

        client.getOutputStream().write(response.getBytes(client));
        client.getOutputStream().flush();
    }
}
