package dev.kspog.pokelite.core;

import dev.kspog.pokelite.api.EventBus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultEventBus implements EventBus {
    private static final Logger LOGGER = Logger.getLogger(DefaultEventBus.class.getName());

    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> listeners =
        new ConcurrentHashMap<>();

    @Override
    public <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener) {
        Consumer<Object> wrapper = event -> listener.accept(eventType.cast(event));
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(wrapper);
        return () -> {
            CopyOnWriteArrayList<Consumer<Object>> registered = listeners.get(eventType);
            if (registered != null) {
                registered.remove(wrapper);
            }
        };
    }

    @Override
    public void post(Object event) {
        if (event == null) {
            return;
        }
        listeners.forEach((eventType, registered) -> {
            if (!eventType.isInstance(event)) {
                return;
            }
            for (Consumer<Object> listener : registered) {
                try {
                    listener.accept(event);
                } catch (RuntimeException error) {
                    LOGGER.log(Level.WARNING, "Event listener failed for " + eventType.getName(), error);
                }
            }
        });
    }
}
