package com.github.leyland.letool.cache.core;

/**
 * Publishes L1 invalidation messages to other JVMs.
 */
@FunctionalInterface
public interface CacheInvalidationPublisher {

    void publish(CacheInvalidationMessage message);

    static CacheInvalidationPublisher noop() {
        return message -> { };
    }
}
