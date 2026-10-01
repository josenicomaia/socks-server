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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    return fromArgs(args, metrics, System::getenv);
  }

  /**
   * Package-private seam for testing: {@code env} replaces {@link System#getenv(String)} so
   * tests don't depend on the real environment.
   *
   * @throws IllegalArgumentException for malformed arguments (unknown option, invalid port)
   * @throws IllegalStateException for an unusable configuration: missing/invalid credentials, or
   *     {@link #NO_AUTH_FLAG} combined with credentials. The caller (see {@link
   *     br.com.nicomaia.server.Main}) must refuse to start in both cases.
   */
  static ServerConfig fromArgs(String[] args, Metrics metrics, Function<String, String> env) {
    boolean noAuth = false;
    List<String> positional = new ArrayList<>();
    for (String arg : args) {
      if (NO_AUTH_FLAG.equals(arg)) {
        noAuth = true;
      } else if (arg.startsWith("-")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      } else {
        positional.add(arg);
      }
    }
    if (positional.size() > 1) {
      throw new IllegalArgumentException("Unexpected argument: " + positional.get(1));
    }

    int port = positional.isEmpty() ? DEFAULT_PORT : parsePort(positional.get(0));

    Map<AddressType, InetResolver> resolvers =
        Map.of(
            AddressType.IPV4, new IpInetResolver(),
            AddressType.IPV6, new IpInetResolver(),
            AddressType.DOMAIN_NAME, new DomainInetResolver());

    AddressResolver addressResolver = new AddressResolver(resolvers);

    HandlersHolder handlers = new HandlersHolder();
    handlers.register(CommandType.CONNECT, new ConnectHandler(metrics));

    return new ServerConfig(port, addressResolver, handlers, authenticator(noAuth, env));
  }

  private static Socks5Authenticator authenticator(boolean noAuth, Function<String, String> env) {
    if (!noAuth) {
      return Socks5Authenticator.requiring(Socks5Credentials.fromEnvironment(env));
    }
    // Contradictory configuration must fail closed: an operator who set credentials believes
    // the proxy is protected, so silently ignoring them would expose an open proxy.
    if (Socks5Credentials.isConfigured(env)) {
      throw new IllegalStateException(
          NO_AUTH_FLAG
              + " conflicts with "
              + Socks5Credentials.USERNAME_ENV
              + "/"
              + Socks5Credentials.PASSWORD_ENV
              + ": unset them to run without authentication, or drop "
              + NO_AUTH_FLAG);
    }
    return Socks5Authenticator.withoutAuthentication();
  }

  private static int parsePort(String value) {
    int port;
    try {
      port = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid port: " + value);
    }
    if (port < 1 || port > 65_535) {
      throw new IllegalArgumentException("Port out of range (1-65535): " + port);
    }
    return port;
  }

  public boolean authenticationRequired() {
    return authenticator.isAuthenticationRequired();
  }
}
