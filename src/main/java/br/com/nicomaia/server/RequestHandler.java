package br.com.nicomaia.server;

import java.io.IOException;
import java.net.Socket;

public class RequestHandler {
    public RequestResponse handle(RequestCommand command) {
        switch (command.getCommandType()) {
            default:
            case CONNECT:
                try {
                    Socket socket = new Socket(command.getAddress(), command.getPort());
                    Session.getInstance().set("connection", socket);

                    return new RequestResponse(
                            command.getSocksVersion(),
                            RequestResponseType.SUCCEEDED,
                            command.getAddressType(),
                            socket.getLocalAddress(),
                            socket.getLocalPort());
                } catch (IOException e) {
                    e.printStackTrace();

                    return new RequestResponse(
                            command.getSocksVersion(),
                            RequestResponseType.SOCKS_SERVER_FAILURE,
                            command.getAddressType(),
                            command.getAddress(),
                            command.getPort());
                }
        }
    }
}
