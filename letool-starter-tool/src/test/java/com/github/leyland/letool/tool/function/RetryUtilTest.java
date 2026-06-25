package com.github.leyland.letool.tool.function;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryUtilTest {

    @Test
    void retrySuccess() throws Exception {
        Callable<String> task = () -> "ok";
        String result = RetryUtil.retry(task, 3, 10);
        assertEquals("ok", result);
    }

    @Test
    void retryWithFailures() {
        AtomicInteger count = new AtomicInteger(0);
        Callable<String> task = () -> {
            if (count.incrementAndGet() < 3) throw new RuntimeException("fail");
            return "success";
        };
        String result = RetryUtil.retry(task, 5, 10);
        assertEquals("success", result);
        assertEquals(3, count.get());
    }

    @Test
    void retryExhausted() {
        AtomicInteger count = new AtomicInteger(0);
        Callable<String> task = () -> {
            count.incrementAndGet();
            throw new RuntimeException("always fail");
        };
        assertThrows(RuntimeException.class, () -> RetryUtil.retry(task, 2, 10));
        assertEquals(3, count.get()); // original + 2 retries
    }

    @Test
    void retryExponentialSuccess() {
        AtomicInteger count = new AtomicInteger(0);
        Callable<String> task = () -> {
            if (count.incrementAndGet() < 2) throw new RuntimeException("fail");
            return "ok";
        };
        String result = RetryUtil.retryExponential(task, 3, 10);
        assertEquals("ok", result);
    }
}
