package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;

public class ConnectHandler implements CommandHandler {
    @Override
    public Mono<Void> handle(NettyInbound inbound, NettyOutbound outbound, Command command, Flux<Object> dataStream) {
        return reactor.netty.tcp.TcpClient.create()
                .host(command.getAddress())
                .port(command.getPort())
                .connect()
                .flatMap(targetConnection -> {
                    java.net.InetSocketAddress localAddress = (java.net.InetSocketAddress) targetConnection.channel().localAddress();
                    
                    Socks5AddressType addrType = (localAddress.getAddress() instanceof java.net.Inet4Address) 
                        ? Socks5AddressType.IPv4 : Socks5AddressType.IPv6;

                    return outbound.sendObject(new DefaultSocks5CommandResponse(
                            Socks5CommandStatus.SUCCESS,
                            addrType,
                            localAddress.getAddress().getHostAddress(),
                            localAddress.getPort()))
                            .then()
                            .then(Mono.defer(() -> {
                                // Remove SOCKS codecs now that we are done with SOCKS protocol
                                outbound.withConnection(connection -> {
                                    try {
                                        connection.channel().pipeline().remove(io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder.class);
                                        connection.channel().pipeline().remove(io.netty.handler.codec.socksx.v5.Socks5ServerEncoder.class);
                                    } catch (Exception e) {
                                        // Ignore
                                    }
                                });
                                return Mono.empty();
                            }))
                            .then(Mono.when(
                                    // Client -> Target
                                    // dataStream contains the rest of the packets from client.
                                    // They should be ByteBufs after removing decoders?
                                    // ByteToMessageDecoder might need 'handlerRemoved' to pass stuff?
                                    // If we cast to ByteBuf.
                                    targetConnection.outbound().send(dataStream.map(obj -> {
                                        if (obj instanceof ByteBuf) return ((ByteBuf) obj).retain();
                                        throw new IllegalArgumentException("Unexpected object type: " + obj.getClass());
                                    })).then(),
                                    // Target -> Client
                                    outbound.send(targetConnection.inbound().receive().retain()).then()
                            ));
                })
                .onErrorResume(e -> {
                    e.printStackTrace();
                    return outbound.sendObject(new DefaultSocks5CommandResponse(
                            Socks5CommandStatus.FAILURE,
                            Socks5AddressType.IPv4,
                            "0.0.0.0", 0))
                            .then();
                });
    }
}
