package br.com.nicomaia.server.protocol;

import br.com.nicomaia.server.commands.Command;
import br.com.nicomaia.server.commands.CommandType;
import br.com.nicomaia.server.commands.handlers.HandlersHolder;
import br.com.nicomaia.server.net.Address;
import br.com.nicomaia.server.net.AddressResolver;
import br.com.nicomaia.server.net.AddressType;
import br.com.nicomaia.server.net.ResolverNotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SocksProtocolHandler {

    private static final Logger logger = Logger.getLogger(SocksProtocolHandler.class.getName());

    private final AddressResolver addressResolver;
    private final HandlersHolder handlers;

    public SocksProtocolHandler(AddressResolver addressResolver, HandlersHolder handlers) {
        this.addressResolver = addressResolver;
        this.handlers = handlers;
    }

    public void handle(Socket clientSocket) {
        try {
            InputStream in = clientSocket.getInputStream();

            // --- Auth Negotiation ---
            byte[] buffer = new byte[2];
            in.read(buffer);

            byte socksVersion = buffer[0];
            byte availableClientAuthTypes = buffer[1];

            buffer = new byte[availableClientAuthTypes];
            in.read(buffer);

            var authRequest = new AuthRequest(socksVersion, availableClientAuthTypes, SupportedAuthType.valueOf(buffer));
            var authResponse = new AuthResponse(socksVersion, SupportedAuthType.NO_AUTH);

            logger.info(authRequest.toString());
            logger.info(authResponse.toString());

            clientSocket.getOutputStream().write(authResponse.toBytes());

            // --- Command ---
            buffer = new byte[4];
            in.read(buffer);

            socksVersion = buffer[0];
            CommandType commandType = CommandType.valueOf(buffer[1]);
            AddressType addressType = AddressType.valueOf(buffer[3]);

            Address address = SocketReader.readAddress(addressType, in);
            InetAddress inetAddress = addressResolver.resolve(address);
            int port = SocketReader.readPort(in);

            var command = new Command(socksVersion, commandType, addressType, inetAddress, port);
            logger.info(command.toString());

            handlers.get(commandType).handle(clientSocket, command);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling SOCKS connection", e);
        }
    }
}
