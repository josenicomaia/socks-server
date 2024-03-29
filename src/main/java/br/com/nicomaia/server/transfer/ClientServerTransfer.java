package br.com.nicomaia.server.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientServerTransfer {
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    private Thread clientToServerThread;
    private Thread serverToClientThread;

    public ClientServerTransfer(Socket client, Socket server) {
        prepareTransfers(client, server);
    }

    private void prepareTransfers(Socket client, Socket server) {
        clientToServerThread = new Thread(() -> {
            try {
                while (client.isConnected()) {
                    transferTo(client.getInputStream(), server.getOutputStream());
                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();

                try {
                    client.close();
                    server.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }, client + " => " + server);

        serverToClientThread = new Thread(() -> {
            try {
                while (server.isConnected()) {
                    transferTo(server.getInputStream(), client.getOutputStream());
                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();

                try {
                    client.close();
                    server.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }, client + " <= " + server);
    }

    private void transferTo(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;

        while ((read = in.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    public void start() {
        serverToClientThread.start();
        clientToServerThread.start();
    }
}
