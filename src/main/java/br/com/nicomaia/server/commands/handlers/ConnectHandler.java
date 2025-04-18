package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.transfer.ClientServerTransfer;
import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandResponse;
import br.com.nicomaia.server.commands.FailureCommandResponse;
import br.com.nicomaia.server.commands.SuccessCommandResponse;

import java.io.IOException;
import java.net.Socket;

public class ConnectHandler implements CommandHandler {
    @Override
    public void handle(Socket client, Command command) {
        Socket proxiedConnection = null;
        try {
            proxiedConnection = new Socket(command.getAddress(), command.getPort());
            var response = new SuccessCommandResponse(command);

            ClientServerTransfer transfer = new ClientServerTransfer(client, proxiedConnection);

            // Send response before starting the transfer to ensure the client gets the response
            sendResponse(client, response);

            // Start the transfer after sending the response
            transfer.start();
        } catch (IOException e) {
            e.printStackTrace();

            try {
                // Close the proxied connection if it was created
                if (proxiedConnection != null && !proxiedConnection.isClosed()) {
                    try {
                        proxiedConnection.close();
                    } catch (IOException closeEx) {
                        closeEx.printStackTrace();
                    }
                }

                sendResponse(client, new FailureCommandResponse(command));
            } catch (IOException ex) {
                ex.printStackTrace();

                // Try to close the client socket if everything else failed
                try {
                    if (!client.isClosed()) {
                        client.close();
                    }
                } catch (IOException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }

    private void sendResponse(Socket client, CommandResponse response) throws IOException {
        System.out.println(response);

        client.getOutputStream().write(response.getBytes());
        client.getOutputStream().flush();
    }
}
