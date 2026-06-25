package com.github.leyland.letool.thread.propagation;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Executor;

public final class ContextPropagateExecutor {

    private ContextPropagateExecutor() {}

    public static Runnable wrap(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                task.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    public static Executor wrap(Executor executor) {
        return task -> executor.execute(wrap(task));
    }
}
