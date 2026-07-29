package dev.kspog.pokelite.api;

import java.util.function.Consumer;

public interface EventBus {
    <T> Subscription subscribe(Class<T> eventType, Consumer<? super T> listener);

    void post(Object event);

    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
