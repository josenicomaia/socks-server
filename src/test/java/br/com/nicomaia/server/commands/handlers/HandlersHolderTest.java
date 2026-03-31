package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.CommandType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HandlersHolderTest {

    @Test
    void shouldRegisterAndRetrieveHandler() {
        HandlersHolder holder = new HandlersHolder();
        CommandHandler handler = mock(CommandHandler.class);

        holder.register(CommandType.CONNECT, handler);

        assertSame(handler, holder.get(CommandType.CONNECT));
    }

    @Test
    void shouldReturnNullForUnregisteredType() {
        HandlersHolder holder = new HandlersHolder();

        assertNull(holder.get(CommandType.BIND));
    }

    @Test
    void shouldOverwriteExistingHandler() {
        HandlersHolder holder = new HandlersHolder();
        CommandHandler first = mock(CommandHandler.class);
        CommandHandler second = mock(CommandHandler.class);

        holder.register(CommandType.CONNECT, first);
        holder.register(CommandType.CONNECT, second);

        assertSame(second, holder.get(CommandType.CONNECT));
    }

    @Test
    void shouldSupportMultipleCommandTypes() {
        HandlersHolder holder = new HandlersHolder();
        CommandHandler connectHandler = mock(CommandHandler.class);
        CommandHandler bindHandler = mock(CommandHandler.class);

        holder.register(CommandType.CONNECT, connectHandler);
        holder.register(CommandType.BIND, bindHandler);

        assertSame(connectHandler, holder.get(CommandType.CONNECT));
        assertSame(bindHandler, holder.get(CommandType.BIND));
    }
}
