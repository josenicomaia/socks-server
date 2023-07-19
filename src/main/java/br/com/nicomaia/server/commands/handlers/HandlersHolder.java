package br.com.nicomaia.server.commands.handlers;

import br.com.nicomaia.server.commands.CommandType;

import java.util.HashMap;
import java.util.Map;

public class HandlersHolder {
    private final Map<CommandType, CommandHandler> handlers;

    public HandlersHolder() {
        handlers = new HashMap<>();
    }

    public void register(CommandType commandType, CommandHandler handler) {
        handlers.put(commandType, handler);
    }

    public CommandHandler get(CommandType commandType) {
        return handlers.get(commandType);
    }
}
