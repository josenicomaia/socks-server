package br.com.nicomaia.server.config;

import br.com.nicomaia.server.auth.Socks5Credentials;
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
import java.util.function.Supplier;

public record ServerConfig(
    int port,
    AddressResolver addressResolver,
    HandlersHolder handlers,
    Socks5Credentials credentials) {

  private static final int DEFAULT_PORT = 5353;

  public static ServerConfig fromArgs(String[] args, Metrics metrics) {
    return fromArgs(args, metrics, Socks5Credentials::fromEnvironment);
  }

  /**
   * Package-private seam for testing: lets tests supply credentials (or a supplier that throws)
   * without touching real environment variables. Production code always goes through {@link
   * #fromArgs(String[], Metrics)}, which reads {@code SOCKS_USERNAME}/{@code SOCKS_PASSWORD}.
   *
   * <p>Deliberately does not catch exceptions from {@code credentialsSupplier}: a missing/blank
   * credential must propagate as {@link IllegalStateException} so the caller (see {@link
   * br.com.nicomaia.server.Main}) can refuse to start the server.
   */
  static ServerConfig fromArgs(
      String[] args, Metrics metrics, Supplier<Socks5Credentials> credentialsSupplier) {
    int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;

    Map<AddressType, InetResolver> resolvers =
        Map.of(
            AddressType.IPV4, new IpInetResolver(),
            AddressType.IPV6, new IpInetResolver(),
            AddressType.DOMAIN_NAME, new DomainInetResolver());

    AddressResolver addressResolver = new AddressResolver(resolvers);

    HandlersHolder handlers = new HandlersHolder();
    handlers.register(CommandType.CONNECT, new ConnectHandler(metrics));

    Socks5Credentials credentials = credentialsSupplier.get();

    return new ServerConfig(port, addressResolver, handlers, credentials);
  }
}
