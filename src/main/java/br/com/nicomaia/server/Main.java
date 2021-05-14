package br.com.nicomaia.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Main {
    public static Map<String, Integer> schemaWithPort;

    static {
        schemaWithPort = new HashMap<>() {{
            put("http", 80);
            put("https", 443);
        }};
    }

    public static void main(String[] args) {
        try {
            var serverSocket = new ServerSocket(8089, 50, InetAddress.getLoopbackAddress());
            System.out.println(serverSocket);

            try (var socket = serverSocket.accept()) {
                System.out.println(socket);

                byte[] greeting = new byte[20];
                int bytesRead = socket.getInputStream().read(greeting);
                System.out.println(bytesRead);
                System.out.println(Arrays.toString(greeting));

                Set<AuthType> supportedAuthTypes = AuthType.valueOf(greeting[1]);
                System.out.println(supportedAuthTypes);



                // [5, 2, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0] // sem usuário e senha
                // [5, 3, 0, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0] // com usuário e senha

//                int bytesRead = socket.getInputStream().read(version, 0, 1);
//                System.out.println(Arrays.toString(version));
//
//                try (var inputStreamReader = new InputStreamReader(socket.getInputStream())) {
//                    try (var bufferedReader = new BufferedReader(inputStreamReader)) {
//                        String httpQuery = bufferedReader.readLine();
//                        String[] requestParts = httpQuery.split(" ", 3);
//                        URI uri = URI.create(requestParts[1]);
//                        System.out.println(uri.getHost() + " " + getPort(uri));
//
//                        try (var forwardSocket = new Socket(uri.getHost(), getPort(uri))) {
////                            socket.getInputStream().transferTo(forwardSocket.getOutputStream());
//                            var printWriter = new PrintWriter(forwardSocket.getOutputStream());
//                            bufferedReader.transferTo(printWriter);
//                        }
//                    }
//                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getPort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }

        return schemaWithPort.getOrDefault(uri.getScheme().toLowerCase(), -1);
    }
}
