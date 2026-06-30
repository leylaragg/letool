package com.github.leyland.letool.lock.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LockProperties} 的单元测试 —— 验证分布式锁配置属性类的默认值与 setter/getter 行为。
 */
@DisplayName("LockProperties 配置属性测试")
class LockPropertiesTest {

    // ======================== 顶层属性测试 ========================

    @Nested
    @DisplayName("顶层属性测试")
    class TopLevelPropertiesTests {

        @Test
        @DisplayName("enabled 默认应为 true")
        void enabledDefaultShouldBeTrue() {
            LockProperties props = new LockProperties();
            assertTrue(props.isEnabled(), "enabled 默认值应为 true");
        }

        @Test
        @DisplayName("enabled 可通过 setter 修改")
        void enabledShouldBeSettable() {
            LockProperties props = new LockProperties();
            props.setEnabled(false);
            assertFalse(props.isEnabled());
        }

        @Test
        @DisplayName("backend 默认应为 redis")
        void backendDefaultShouldBeRedis() {
            LockProperties props = new LockProperties();
            assertEquals("redis", props.getBackend(), "backend 默认值应为 'redis'");
        }

        @Test
        @DisplayName("backend 可通过 setter 修改")
        void backendShouldBeSettable() {
            LockProperties props = new LockProperties();
            props.setBackend("zookeeper");
            assertEquals("zookeeper", props.getBackend());
        }

        @Test
        @DisplayName("pessimistic 子配置不应为 null")
        void pessimisticShouldNotBeNull() {
            LockProperties props = new LockProperties();
            assertNotNull(props.getPessimistic(), "pessimistic 子配置不应为 null");
        }

        @Test
        @DisplayName("pessimistic 子配置可通过 setter 替换")
        void pessimisticShouldBeSettable() {
            LockProperties props = new LockProperties();
            LockProperties.Pessimistic newPessimistic = new LockProperties.Pessimistic();
            newPessimistic.setLockPrefix("custom:lock:");
            props.setPessimistic(newPessimistic);
            assertEquals("custom:lock:", props.getPessimistic().getLockPrefix());
        }

        @Test
        @DisplayName("idempotent 子配置不应为 null")
        void idempotentShouldNotBeNull() {
            LockProperties props = new LockProperties();
            assertNotNull(props.getIdempotent(), "idempotent 子配置不应为 null");
        }

        @Test
        @DisplayName("idempotent 子配置可通过 setter 替换")
        void idempotentShouldBeSettable() {
            LockProperties props = new LockProperties();
            LockProperties.Idempotent newIdempotent = new LockProperties.Idempotent();
            newIdempotent.setTtl(7200);
            props.setIdempotent(newIdempotent);
            assertEquals(7200, props.getIdempotent().getTtl());
        }
    }

    // ======================== Pessimistic 悲观锁配置测试 ========================

    @Nested
    @DisplayName("Pessimistic 悲观锁配置测试")
    class PessimisticTests {

        @Test
        @DisplayName("lockPrefix 默认应为 'letool:lock:'")
        void lockPrefixDefaultShouldBeCorrect() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            assertEquals("letool:lock:", p.getLockPrefix(), "lockPrefix 默认值应为 'letool:lock:'");
        }

        @Test
        @DisplayName("lockPrefix 可通过 setter 修改")
        void lockPrefixShouldBeSettable() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            p.setLockPrefix("myapp:lock:");
            assertEquals("myapp:lock:", p.getLockPrefix());
        }

        @Test
        @DisplayName("defaultLeaseTime 默认应为 30")
        void defaultLeaseTimeDefaultShouldBe30() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            assertEquals(30L, p.getDefaultLeaseTime(), "defaultLeaseTime 默认值应为 30");
        }

        @Test
        @DisplayName("defaultLeaseTime 可通过 setter 修改")
        void defaultLeaseTimeShouldBeSettable() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            p.setDefaultLeaseTime(60L);
            assertEquals(60L, p.getDefaultLeaseTime());
        }

        @Test
        @DisplayName("defaultWaitTime 默认应为 3")
        void defaultWaitTimeDefaultShouldBe3() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            assertEquals(3L, p.getDefaultWaitTime(), "defaultWaitTime 默认值应为 3");
        }

        @Test
        @DisplayName("defaultWaitTime 可通过 setter 修改")
        void defaultWaitTimeShouldBeSettable() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            p.setDefaultWaitTime(10L);
            assertEquals(10L, p.getDefaultWaitTime());
        }

        @Test
        @DisplayName("fairLock 默认应为 false")
        void fairLockDefaultShouldBeFalse() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            assertFalse(p.isFairLock(), "fairLock 默认值应为 false");
        }

        @Test
        @DisplayName("fairLock 可通过 setter 修改")
        void fairLockShouldBeSettable() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            p.setFairLock(true);
            assertTrue(p.isFairLock());
        }

        @Test
        @DisplayName("autoRenewal 默认应为 true")
        void autoRenewalDefaultShouldBeTrue() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            assertTrue(p.isAutoRenewal(), "autoRenewal 默认值应为 true");
        }

        @Test
        @DisplayName("autoRenewal 可通过 setter 修改")
        void autoRenewalShouldBeSettable() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            p.setAutoRenewal(false);
            assertFalse(p.isAutoRenewal());
        }

        @Test
        @DisplayName("renewalInterval 默认应为 10")
        void renewalIntervalDefaultShouldBe10() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            assertEquals(10L, p.getRenewalInterval(), "renewalInterval 默认值应为 10");
        }

        @Test
        @DisplayName("renewalInterval 可通过 setter 修改")
        void renewalIntervalShouldBeSettable() {
            LockProperties.Pessimistic p = new LockProperties.Pessimistic();
            p.setRenewalInterval(15L);
            assertEquals(15L, p.getRenewalInterval());
        }
    }

    // ======================== Idempotent 幂等配置测试 ========================

    @Nested
    @DisplayName("Idempotent 幂等配置测试")
    class IdempotentTests {

        @Test
        @DisplayName("enabled 默认应为 true")
        void enabledDefaultShouldBeTrue() {
            LockProperties.Idempotent idempotent = new LockProperties.Idempotent();
            assertTrue(idempotent.isEnabled(), "enabled 默认值应为 true");
        }

        @Test
        @DisplayName("enabled 可通过 setter 修改")
        void enabledShouldBeSettable() {
            LockProperties.Idempotent idempotent = new LockProperties.Idempotent();
            idempotent.setEnabled(false);
            assertFalse(idempotent.isEnabled());
        }

        @Test
        @DisplayName("keyPrefix 默认应为 'letool:idempotent:'")
        void keyPrefixDefaultShouldBeCorrect() {
            LockProperties.Idempotent idempotent = new LockProperties.Idempotent();
            assertEquals("letool:idempotent:", idempotent.getKeyPrefix(),
                    "keyPrefix 默认值应为 'letool:idempotent:'");
        }

        @Test
        @DisplayName("keyPrefix 可通过 setter 修改")
        void keyPrefixShouldBeSettable() {
            LockProperties.Idempotent idempotent = new LockProperties.Idempotent();
            idempotent.setKeyPrefix("myapp:idem:");
            assertEquals("myapp:idem:", idempotent.getKeyPrefix());
        }

        @Test
        @DisplayName("ttl 默认应为 86400（24 小时）")
        void ttlDefaultShouldBe86400() {
            LockProperties.Idempotent idempotent = new LockProperties.Idempotent();
            assertEquals(86400L, idempotent.getTtl(), "ttl 默认值应为 86400");
        }

        @Test
        @DisplayName("ttl 可通过 setter 修改")
        void ttlShouldBeSettable() {
            LockProperties.Idempotent idempotent = new LockProperties.Idempotent();
            idempotent.setTtl(7200L);
            assertEquals(7200L, idempotent.getTtl());
        }
    }

    // ======================== 整合测试 ========================

    @Nested
    @DisplayName("整合测试")
    class IntegrationTests {

        @Test
        @DisplayName("各级配置默认值应组成完整的默认配置")
        void allDefaultsShouldFormCompleteConfiguration() {
            LockProperties props = new LockProperties();

            // 顶层
            assertTrue(props.isEnabled());
            assertEquals("redis", props.getBackend());

            // 悲观锁
            assertNotNull(props.getPessimistic());
            assertEquals("letool:lock:", props.getPessimistic().getLockPrefix());
            assertEquals(30L, props.getPessimistic().getDefaultLeaseTime());
            assertEquals(3L, props.getPessimistic().getDefaultWaitTime());
            assertFalse(props.getPessimistic().isFairLock());
            assertTrue(props.getPessimistic().isAutoRenewal());
            assertEquals(10L, props.getPessimistic().getRenewalInterval());

            // 幂等
            assertNotNull(props.getIdempotent());
            assertTrue(props.getIdempotent().isEnabled());
            assertEquals("letool:idempotent:", props.getIdempotent().getKeyPrefix());
            assertEquals(86400L, props.getIdempotent().getTtl());
        }

        @Test
        @DisplayName("Pessimistic 和 Idempotent 为独立实例")
        void pessimisticAndIdempotentAreIndependentInstances() {
            LockProperties props = new LockProperties();
            assertNotSame(props.getPessimistic(), props.getIdempotent(),
                    "Pessimistic 和 Idempotent 应为不同实例");
        }
    }
}
