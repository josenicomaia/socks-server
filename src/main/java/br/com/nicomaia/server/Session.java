package br.com.nicomaia.server;

import java.util.HashMap;
import java.util.Map;

public class Session {
    static private final Session instance = new Session();

    private final Map<String, Object> objectMap = new HashMap<>();

    private Session() {
    }

    public static Session getInstance() {
        return instance;
    }

    public <T> T get(String key) {
        return (T) objectMap.get(key);
    }

    public void set(String key, Object object) {
        objectMap.put(key, object);
    }
}
