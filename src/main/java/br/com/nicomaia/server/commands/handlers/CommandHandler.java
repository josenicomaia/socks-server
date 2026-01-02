package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.Command;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyInbound;
import reactor.netty.NettyOutbound;

public interface CommandHandler {
    Mono<Void> handle(NettyInbound inbound, NettyOutbound outbound, Command command, Flux<Object> dataStream);
}
