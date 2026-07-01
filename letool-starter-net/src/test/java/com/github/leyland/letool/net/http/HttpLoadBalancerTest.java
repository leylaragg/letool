package com.github.leyland.letool.net.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HttpLoadBalancer HTTP 负载均衡器测试")
class HttpLoadBalancerTest {

    private HttpLoadBalancer balancer;

    @Nested
    @DisplayName("空服务器列表")
    class EmptyServerList {

        @BeforeEach
        void setUp() {
            balancer = new HttpLoadBalancer();
        }

        @Test
        @DisplayName("无服务器时 getServers 返回空列表")
        void noServersReturnsEmpty() {
            assertTrue(balancer.getServers().isEmpty());
        }

        @Test
        @DisplayName("无服务器时 getHealthyCount 返回 0")
        void healthyCountIsZero() {
            assertEquals(0, balancer.getHealthyCount());
        }

        @Test
        @DisplayName("无服务器时 next 返回 null")
        void nextReturnsNull() {
            assertNull(balancer.next());
        }

        @Test
        @DisplayName("无服务器时 nextWeighted 返回 null")
        void nextWeightedReturnsNull() {
            assertNull(balancer.nextWeighted());
        }
    }

    @Nested
    @DisplayName("轮询负载均衡")
    class RoundRobin {

        @BeforeEach
        void setUp() {
            balancer = new HttpLoadBalancer();
            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.1").port(8080).weight(1).build());
            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.2").port(8080).weight(1).build());
            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.3").port(8080).weight(1).build());
        }

        @Test
        @DisplayName("轮询依次返回各服务器")
        void roundRobinCycles() {
            assertEquals("10.0.0.1", balancer.next().getHost());
            assertEquals("10.0.0.2", balancer.next().getHost());
            assertEquals("10.0.0.3", balancer.next().getHost());
            // wraps around
            assertEquals("10.0.0.1", balancer.next().getHost());
        }
    }

    @Nested
    @DisplayName("加权轮询")
    class WeightedRoundRobin {

        @BeforeEach
        void setUp() {
            balancer = new HttpLoadBalancer();
            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.1").port(8080).weight(10).build());
            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.2").port(8080).weight(0).build());
        }

        @Test
        @DisplayName("权重为0的服务器不会被选中")
        void zeroWeightNeverSelected() {
            for (int i = 0; i < 20; i++) {
                HttpLoadBalancer.BackendServer server = balancer.nextWeighted();
                assertNotEquals("10.0.0.2", server.getHost());
            }
        }

        @Test
        @DisplayName("高权重服务器更频繁被选中")
        void higherWeightMoreFrequent() {
            int count1 = 0;
            for (int i = 0; i < 100; i++) {
                if (balancer.nextWeighted().getHost().equals("10.0.0.1")) {
                    count1++;
                }
            }
            assertEquals(100, count1); // only healthy server with weight > 0
        }
    }

    @Nested
    @DisplayName("服务器增删")
    class AddRemove {

        @BeforeEach
        void setUp() {
            balancer = new HttpLoadBalancer();
        }

        @Test
        @DisplayName("添加和删除服务器")
        void addAndRemoveServer() {
            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.1").port(8080).weight(1).build());
            assertEquals(1, balancer.getServers().size());

            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.2").port(8080).weight(1).build());
            assertEquals(2, balancer.getServers().size());

            balancer.removeServer("10.0.0.1");
            assertEquals(1, balancer.getServers().size());
            assertEquals("10.0.0.2", balancer.getServers().get(0).getHost());
        }

        @Test
        @DisplayName("删除不存在的服务器不影响列表")
        void removeUnknownServerNoChange() {
            balancer.addServer(HttpLoadBalancer.BackendServer.builder().host("10.0.0.1").port(8080).weight(1).build());
            balancer.removeServer("nonexistent");
            assertEquals(1, balancer.getServers().size());
        }
    }

    @Nested
    @DisplayName("健康服务器过滤")
    class HealthyServers {

        @Test
        @DisplayName("next 仅返回健康服务器")
        void nextSkipsUnhealthy() {
            balancer = new HttpLoadBalancer();
            HttpLoadBalancer.BackendServer healthy = HttpLoadBalancer.BackendServer.builder().host("10.0.0.1").port(8080).weight(1).build();
            HttpLoadBalancer.BackendServer unhealthy = HttpLoadBalancer.BackendServer.builder().host("10.0.0.2").port(8080).weight(1).healthy(false).build();

            balancer.addServer(healthy);
            balancer.addServer(unhealthy);

            assertEquals(1, balancer.getHealthyCount());
            assertEquals("10.0.0.1", balancer.next().getHost());
            assertEquals("10.0.0.1", balancer.next().getHost());
        }

        @Test
        @DisplayName("所有服务器不健康时返回第一个作为降级")
        void allUnhealthyReturnsFirst() {
            balancer = new HttpLoadBalancer();
            HttpLoadBalancer.BackendServer s1 = HttpLoadBalancer.BackendServer.builder().host("10.0.0.1").port(8080).weight(1).healthy(false).build();
            balancer.addServer(s1);
            assertEquals(0, balancer.getHealthyCount());
            // next() returns first server as fallback when all unhealthy
            assertNotNull(balancer.next());
            assertEquals("10.0.0.1", balancer.next().getHost());
        }
    }
}
