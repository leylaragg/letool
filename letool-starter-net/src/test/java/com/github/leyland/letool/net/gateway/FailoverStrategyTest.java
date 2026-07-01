package com.github.leyland.letool.net.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FailoverStrategy 故障转移策略测试")
class FailoverStrategyTest {

    private List<BackendServer> servers;
    private BackendServer server1;
    private BackendServer server2;
    private BackendServer server3;

    @BeforeEach
    void setUpServers() {
        server1 = BackendServer.builder().host("10.0.0.1").port(8080).build();
        server2 = BackendServer.builder().host("10.0.0.2").port(8080).build();
        server3 = BackendServer.builder().host("10.0.0.3").port(8080).build();
        servers = new ArrayList<>(Arrays.asList(server1, server2, server3));
    }

    @Nested
    @DisplayName("RETRY_NEXT - 重试下一个节点")
    class RetryNext {

        @Test
        @DisplayName("返回列表中下一个节点")
        void returnsNextServer() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_NEXT, 3, 3);
            BackendServer next = strategy.selectNext(servers, server1);
            assertEquals(server2, next);
        }

        @Test
        @DisplayName("当前为最后一个时返回第一个")
        void wrapsAroundToFirst() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_NEXT, 3, 3);
            BackendServer next = strategy.selectNext(servers, server3);
            assertEquals(server1, next);
        }

        @Test
        @DisplayName("当前节点不在列表中返回第一个")
        void currentNotFoundReturnsFirst() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_NEXT, 3, 3);
            BackendServer unknown = BackendServer.builder().host("10.0.0.99").port(8080).build();
            BackendServer next = strategy.selectNext(servers, unknown);
            assertEquals(server1, next);
        }

        @Test
        @DisplayName("空列表返回 null")
        void emptyListReturnsNull() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_NEXT, 3, 3);
            assertNull(strategy.selectNext(Collections.emptyList(), server1));
        }
    }

    @Nested
    @DisplayName("RETRY_SAME - 重试同一个节点")
    class RetrySame {

        @Test
        @DisplayName("返回当前节点本身")
        void returnsCurrentServer() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_SAME, 3, 3);
            assertEquals(server1, strategy.selectNext(servers, server1));
        }

        @Test
        @DisplayName("当前为 null 时返回第一个")
        void currentNullReturnsFirst() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_SAME, 3, 3);
            assertEquals(server1, strategy.selectNext(servers, null));
        }
    }

    @Nested
    @DisplayName("FAIL_FAST - 快速失败")
    class FailFast {

        @Test
        @DisplayName("返回 null 表示不重试")
        void returnsNull() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.FAIL_FAST, 0, 3);
            assertNull(strategy.selectNext(servers, server1));
        }
    }

    @Nested
    @DisplayName("失败计数与自动标记不健康")
    class FailureCounting {

        @Test
        @DisplayName("recordFailure 增加计数")
        void recordFailureIncrementsCount() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_NEXT, 3, 3);
            strategy.recordFailure(server1);
            assertEquals(1, strategy.getFailureCount(server1));
            strategy.recordFailure(server1);
            assertEquals(2, strategy.getFailureCount(server1));
        }

        @Test
        @DisplayName("recordSuccess 清零计数")
        void recordSuccessResetsCount() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_NEXT, 3, 3);
            strategy.recordFailure(server1);
            strategy.recordFailure(server1);
            strategy.recordSuccess(server1);
            assertEquals(0, strategy.getFailureCount(server1));
        }

        @Test
        @DisplayName("未知服务器的失败计数为 0")
        void unknownServerCountIsZero() {
            FailoverStrategy strategy = new FailoverStrategy(FailoverStrategy.Strategy.RETRY_NEXT, 3, 3);
            BackendServer unknown = BackendServer.builder().host("unknown").port(8080).build();
            assertEquals(0, strategy.getFailureCount(unknown));
        }
    }

    @Nested
    @DisplayName("策略名称解析")
    class StrategyFromName {

        @Test
        @DisplayName("已知名称解析")
        void knownNames() {
            assertEquals(FailoverStrategy.Strategy.RETRY_NEXT,
                    FailoverStrategy.Strategy.fromName("retry-next"));
            assertEquals(FailoverStrategy.Strategy.RETRY_SAME,
                    FailoverStrategy.Strategy.fromName("retry-same"));
            assertEquals(FailoverStrategy.Strategy.FAIL_FAST,
                    FailoverStrategy.Strategy.fromName("fail-fast"));
        }

        @Test
        @DisplayName("未知名称默认返回 RETRY_NEXT")
        void unknownNameDefaults() {
            assertEquals(FailoverStrategy.Strategy.RETRY_NEXT,
                    FailoverStrategy.Strategy.fromName("unknown-strategy"));
        }

        @Test
        @DisplayName("null 名称默认返回 RETRY_NEXT")
        void nullNameDefaults() {
            assertEquals(FailoverStrategy.Strategy.RETRY_NEXT,
                    FailoverStrategy.Strategy.fromName(null));
        }
    }
}
