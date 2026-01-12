package br.com.nicomaia.server;
import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.ConnectHandler;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;
import reactor.netty.tcp.TcpServer;

public class Main {
    public static void main(String[] args) {
        HandlersHolder handlers = new HandlersHolder();
        handlers.register(CommandType.CONNECT, new ConnectHandler());

        int serverPort = (args.length > 0) ? Integer.parseInt(args[0]) : 5353;

        TcpServer.create()
                .port(serverPort)
                .handle((in, out) -> handleSocks5Connection(in, out, handlers))
                .bindNow()
                .onDispose()
                .block();
    }
    private static Mono<Void> handleSocks5Connection(NettyInbound in, NettyOutbound out, HandlersHolder handlers) {
        return in.withConnection(connection -> {
            connection.addHandlerLast(io.netty.handler.codec.socksx.v5.Socks5ServerEncoder.DEFAULT);
            connection.addHandlerLast(new io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder());
        })
        .receiveObject() // Flux<Object>
        .switchOnFirst((signal, flux) -> {
            if (signal.hasValue()) {
                Object msg = signal.get();
                if (msg instanceof io.netty.handler.codec.socksx.v5.Socks5InitialRequest) {
                    return handleInitialRequest(in, out, (io.netty.handler.codec.socksx.v5.Socks5InitialRequest) msg, handlers, (reactor.core.publisher.Flux<Object>)flux);
                } else {
                    return Mono.error(new IllegalArgumentException("Expected Socks5InitialRequest but got " + msg.getClass()));
                }
            }
            return flux.then();
        })
        .then();
    }

    private static Mono<Void> handleInitialRequest(NettyInbound in, NettyOutbound out, io.netty.handler.codec.socksx.v5.Socks5InitialRequest request, HandlersHolder handlers, reactor.core.publisher.Flux<Object> upstream) {
        // Authenticate (NO_AUTH)
        return out.sendObject(new io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse(io.netty.handler.codec.socksx.v5.Socks5AuthMethod.NO_AUTH))
                .then()
                .then(Mono.defer(() -> {
                    // Prepare for Command
                    in.withConnection(connection -> {
                        connection.addHandlerLast(new io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder());
                    });
                    
                    // We must continue using 'upstream'.
                    // skip(1) to skip the InitialRequest already consumed.
                    return upstream
                            .skip(1)
                            .switchOnFirst((signal, flux) -> {
                                if (signal.hasValue()) {
                                    Object msg = signal.get();
                                     if (msg instanceof io.netty.handler.codec.socksx.v5.Socks5CommandRequest) {
                                         // Pass the rest of the flux (excluding command) to the handler
                                         // But switchOnFirst passes flux starting with the signal.
                                         // So 'flux' contains CommandRequest + rest.
                                         // We want to pass 'rest' to handleCommandRequest.
                                         // So handleCommandRequest will skip(1) or we skip(1) here?
                                         // Better pass 'flux' and let handleCommandRequest consume or skip the command.
                                         // Actually, handleCommandRequest needs the 'CommandRequest' object to know where to connect.
                                         // So we extract it.
                                         return handleCommandRequest(in, out, (io.netty.handler.codec.socksx.v5.Socks5CommandRequest) msg, handlers, flux);
                                     }
                                }
                                return flux.then();
                            })
                            .then();
                }));
    }

    private static Mono<Void> handleCommandRequest(NettyInbound in, NettyOutbound out, io.netty.handler.codec.socksx.v5.Socks5CommandRequest request, HandlersHolder handlers, reactor.core.publisher.Flux<Object> upstream) {
        br.com.nicomaia.server.commands.CommandType type = br.com.nicomaia.server.commands.CommandType.valueOf((byte) request.type().byteValue());
        br.com.nicomaia.server.net.AddressType addrType = br.com.nicomaia.server.net.AddressType.valueOf((byte) request.dstAddrType().byteValue());
        
        Command command = new Command(
            (byte) 5, // SOCKS version 5
            type,
            addrType,
            request.dstAddr(),
            request.dstPort()
        );
        
        var handler = handlers.get(type);
        if (handler != null) {
            // upstream contains [CommandRequest, ... data ...]
            // We want [ ... data ... ]
            return handler.handle(in, out, command, upstream.skip(1));
        } else {
             // Unsupported command
             return out.sendObject(new io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse(
                     io.netty.handler.codec.socksx.v5.Socks5CommandStatus.COMMAND_UNSUPPORTED,
                     request.dstAddrType()))
                     .then();
        }
    }
}
