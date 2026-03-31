package br.com.nicomaia.server.config;

import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.ConnectHandler;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import br.com.nicomaia.server.metrics.Metrics;
import br.com.nicomaia.server.net.AddressResolver;
import br.com.nicomaia.server.net.AddressType;
import br.com.nicomaia.server.net.resolvers.DomainInetResolver;
import br.com.nicomaia.server.net.resolvers.InetResolver;
import br.com.nicomaia.server.net.resolvers.IpInetResolver;
import java.util.Map;

public record ServerConfig(int port, AddressResolver addressResolver, HandlersHolder handlers) {

  private static final int DEFAULT_PORT = 5353;

  public static ServerConfig fromArgs(String[] args, Metrics metrics) {
    int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;

    Map<AddressType, InetResolver> resolvers =
        Map.of(
            AddressType.IPV4, new IpInetResolver(),
            AddressType.IPV6, new IpInetResolver(),
            AddressType.DOMAIN_NAME, new DomainInetResolver());

    AddressResolver addressResolver = new AddressResolver(resolvers);

    HandlersHolder handlers = new HandlersHolder();
    handlers.register(CommandType.CONNECT, new ConnectHandler(metrics));

    return new ServerConfig(port, addressResolver, handlers);
  }
}
