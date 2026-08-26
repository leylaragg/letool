package io.github.leylaragg.letool.lock.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证锁请求在进入具体后端前完成参数归一化和校验。 */
class LockRequestTest {

    /** 看门狗请求不携带固定租约。 */
    @Test
    void watchdogRequestShouldNotCarryLeaseTime() {
        LockRequest request = LockRequest.watchdog("order:1", Duration.ofSeconds(3));
        assertTrue(request.usesWatchdog());
    }

    /** 固定租约请求必须显式携带正数租约。 */
    @Test
    void fixedLeaseRequestShouldCarryLeaseTime() {
        LockRequest request = LockRequest.fixedLease(
                "order:1", Duration.ZERO, Duration.ofSeconds(30));
        assertFalse(request.usesWatchdog());
    }

    /** 空 key、负等待时间和非正固定租约都应尽早失败。 */
    @Test
    void invalidRequestShouldFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> LockRequest.watchdog(" ", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> LockRequest.watchdog("order:1", Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> LockRequest.fixedLease("order:1", Duration.ZERO, Duration.ZERO));
    }
}
