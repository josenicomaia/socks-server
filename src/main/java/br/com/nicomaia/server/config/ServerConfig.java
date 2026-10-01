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
import br.com.nicomaia.server.protocol.Socks5Authenticator;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

public record ServerConfig(
    int port,
    AddressResolver addressResolver,
    HandlersHolder handlers,
    Socks5Authenticator authenticator) {

  /**
   * Opt-out flag that starts the server without authentication. Only for strictly controlled
   * environments (local development, isolated test networks); never on a reachable network.
   */
  public static final String NO_AUTH_FLAG = "--no-auth";

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
   * br.com.nicomaia.server.Main}) can refuse to start the server. With {@link #NO_AUTH_FLAG} the
   * supplier is never called.
   */
  static ServerConfig fromArgs(
      String[] args, Metrics metrics, Supplier<Socks5Credentials> credentialsSupplier) {
    boolean noAuth = Arrays.asList(args).contains(NO_AUTH_FLAG);
    String[] positional =
        Arrays.stream(args).filter(arg -> !NO_AUTH_FLAG.equals(arg)).toArray(String[]::new);

    int port = (positional.length > 0) ? Integer.parseInt(positional[0]) : DEFAULT_PORT;

    Map<AddressType, InetResolver> resolvers =
        Map.of(
            AddressType.IPV4, new IpInetResolver(),
            AddressType.IPV6, new IpInetResolver(),
            AddressType.DOMAIN_NAME, new DomainInetResolver());

    AddressResolver addressResolver = new AddressResolver(resolvers);

    HandlersHolder handlers = new HandlersHolder();
    handlers.register(CommandType.CONNECT, new ConnectHandler(metrics));

    Socks5Authenticator authenticator =
        noAuth
            ? Socks5Authenticator.withoutAuthentication()
            : Socks5Authenticator.requiring(credentialsSupplier.get());

    return new ServerConfig(port, addressResolver, handlers, authenticator);
  }

  public boolean authenticationRequired() {
    return authenticator.isAuthenticationRequired();
  }
}
