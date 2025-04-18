package br.com.nicomaia.server;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.ConnectHandler;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressResolver;
import br.com.nicomaia.server.net.AddressType;
import br.com.nicomaia.server.net.ResolverNotFoundException;
import br.com.nicomaia.server.net.resolvers.DomainInetResolver;
import br.com.nicomaia.server.net.resolvers.InetResolver;
import br.com.nicomaia.server.net.resolvers.IpInetResolver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        Map<AddressType, InetResolver> resolvers = new HashMap<>();
        resolvers.put(AddressType.IPV4, new IpInetResolver());
        resolvers.put(AddressType.IPV6, new IpInetResolver());
        resolvers.put(AddressType.DOMAIN_NAME, new DomainInetResolver());

        AddressResolver addressResolver = new AddressResolver(resolvers);

        HandlersHolder handlers = new HandlersHolder();
        handlers.register(CommandType.CONNECT, new ConnectHandler());

        Thread threadPoolServerThread = new Thread(() -> startThreadPoolServer(args, addressResolver, handlers));
        threadPoolServerThread.setDaemon(true);
        threadPoolServerThread.start();

        startReactorServer(args, addressResolver, handlers);
    }

    private static void startThreadPoolServer(String[] args, AddressResolver addressResolver, HandlersHolder handlers) {
        try {
            var serverPort = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;
            var serverSocket = new ServerSocket(serverPort);
            System.out.println("Server started on port " + serverPort);
            System.out.println(serverSocket);

            ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            while (true) {
                try {
                    var clientSocket = serverSocket.accept();

                    executorService.submit(() -> {
                        try {
                            System.out.printf("Handling client in thread %s...%n", Thread.currentThread());
                            System.out.println(clientSocket);

                            byte[] buffer = new byte[2];
                            clientSocket.getInputStream().read(buffer);

                            byte socksVersion = buffer[0];
                            byte availableClientAuthTypes = buffer[1];

                            buffer = new byte[availableClientAuthTypes];
                            clientSocket.getInputStream().read(buffer);

                            var loginNegotiationCommand = new LoginNegotiationCommand(socksVersion, availableClientAuthTypes, SupportedAuthType.valueOf(buffer));
                            var loginNegotiationResult = new LoginNegotiationResult(socksVersion, SupportedAuthType.NO_AUTH);

                            System.out.println(loginNegotiationCommand);
                            System.out.println(loginNegotiationResult);

                            clientSocket.getOutputStream().write(loginNegotiationResult.getResponse());

                            buffer = new byte[4];
                            clientSocket.getInputStream().read(buffer);

                            socksVersion = buffer[0];
                            CommandType commandType = CommandType.valueOf(buffer[1]);
                            AddressType addressType = AddressType.valueOf(buffer[3]);

                            Address address = readAddressBytes(addressType, clientSocket);
                            InetAddress inetAddress = addressResolver.resolve(address);
                            int port = readPort(clientSocket);

                            var command = new Command(socksVersion, commandType, addressType, inetAddress, port);
                            System.out.println(command);
                            handlers.get(commandType).handle(clientSocket, command);

                            System.out.printf("Finished handling client in thread %s...%n", Thread.currentThread());
                        } catch (IOException | ResolverNotFoundException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startReactorServer(String[] args, AddressResolver addressResolver, HandlersHolder handlers) {
        var serverPort = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;

        DisposableServer server = TcpServer.create()
                .port(serverPort + 1)
                .handle((inbound, outbound) -> {
                    System.out.println("New client connected to reactor server");

                    ByteBuf responseBuffer = Unpooled.wrappedBuffer(new byte[] { 5, 0, 0, 1, 127, 0, 0, 1, 0, 80 });

                    return outbound.send(Mono.just(responseBuffer))
                            .then(
                                inbound.receive()
                                    .doOnNext(data -> System.out.println("Recebido: " + data.readableBytes() + " bytes"))
                                    .flatMap(data -> outbound.send(Mono.just(data.retain())))
                                    .then()
                            );
                })
                .bindNow();

        System.out.println("Reactor server started on port " + (serverPort + 1));
        server.onDispose().block();
    }


    private static Address readAddressBytes(AddressType addressType, Socket clientSocket) throws IOException {
        byte[] buffer = null;

        if (AddressType.IPV6 == addressType) {
            buffer = new byte[16];
        } else if (AddressType.IPV4 == addressType) {
            buffer = new byte[4];
        } else if (AddressType.DOMAIN_NAME == addressType) {
            buffer = new byte[readDomainLength(clientSocket)];
        }

        clientSocket.getInputStream().read(buffer);

        return new Address(buffer, addressType);
    }

    private static byte readDomainLength(Socket clientSocket) throws IOException {
        byte[] domainLengthBuffer = new byte[1];
        clientSocket.getInputStream().read(domainLengthBuffer);

        return domainLengthBuffer[0];
    }

    private static int readPort(Socket clientSocket) throws IOException {
        byte[] buffer = new byte[2];
        clientSocket.getInputStream().read(buffer);

        int port = ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);

        return port;
    }
}
