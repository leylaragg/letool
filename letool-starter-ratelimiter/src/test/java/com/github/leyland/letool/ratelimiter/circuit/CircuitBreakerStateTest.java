package com.github.leyland.letool.ratelimiter.circuit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CircuitBreakerState 熔断器状态枚举测试")
class CircuitBreakerStateTest {

    @Nested
    @DisplayName("枚举值测试")
    class EnumValuesTests {

        @Test
        @DisplayName("应包含三个状态值")
        void shouldHaveThreeStates() {
            CircuitBreakerState[] states = CircuitBreakerState.values();
            assertEquals(3, states.length);
        }

        @Test
        @DisplayName("valueOf 应正确解析各状态")
        void valueOfShouldParseCorrectly() {
            assertEquals(CircuitBreakerState.CLOSED, CircuitBreakerState.valueOf("CLOSED"));
            assertEquals(CircuitBreakerState.OPEN, CircuitBreakerState.valueOf("OPEN"));
            assertEquals(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.valueOf("HALF_OPEN"));
        }

        @Test
        @DisplayName("ordinal 应与声明顺序一致")
        void ordinalShouldMatchDeclarationOrder() {
            assertEquals(0, CircuitBreakerState.CLOSED.ordinal());
            assertEquals(1, CircuitBreakerState.OPEN.ordinal());
            assertEquals(2, CircuitBreakerState.HALF_OPEN.ordinal());
        }
    }

    @Nested
    @DisplayName("name() 测试")
    class NameTests {

        @Test
        @DisplayName("CLOSED 的 name 应为 'CLOSED'")
        void closedNameShouldBeClosed() {
            assertEquals("CLOSED", CircuitBreakerState.CLOSED.name());
        }

        @Test
        @DisplayName("OPEN 的 name 应为 'OPEN'")
        void openNameShouldBeOpen() {
            assertEquals("OPEN", CircuitBreakerState.OPEN.name());
        }

        @Test
        @DisplayName("HALF_OPEN 的 name 应为 'HALF_OPEN'")
        void halfOpenNameShouldBeHalfOpen() {
            assertEquals("HALF_OPEN", CircuitBreakerState.HALF_OPEN.name());
        }
    }
}
