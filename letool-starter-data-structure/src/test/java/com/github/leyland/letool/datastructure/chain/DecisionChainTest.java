package com.github.leyland.letool.datastructure.chain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("决策链测试")
class DecisionChainTest {

    @Nested
    @DisplayName("基本匹配")
    class BasicMatching {

        @Test
        @DisplayName("首个条件命中则执行对应动作")
        void firstMatch() {
            DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
                    .when(n -> n > 10, n -> "大于10")
                    .when(n -> n > 5,  n -> "大于5")
                    .otherwise(n -> "默认")
                    .build();

            assertEquals("大于10", chain.execute(15));
        }

        @Test
        @DisplayName("第二个条件命中时跳过第一个")
        void secondMatch() {
            DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
                    .when(n -> n > 10, n -> "大于10")
                    .when(n -> n > 5,  n -> "大于5")
                    .otherwise(n -> "默认")
                    .build();

            assertEquals("大于5", chain.execute(7));
        }

        @Test
        @DisplayName("无条件命中时走 otherwise 兜底")
        void otherwise() {
            DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
                    .when(n -> n > 10, n -> "大于10")
                    .otherwise(n -> "默认")
                    .build();

            assertEquals("默认", chain.execute(3));
        }

        @Test
        @DisplayName("未设置 otherwise 时返回 null")
        void noOtherwiseReturnsNull() {
            DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
                    .when(n -> n > 10, n -> "大于10")
                    .build();

            assertNull(chain.execute(5));
        }
    }

    @Nested
    @DisplayName("构建器校验")
    class BuilderValidation {

        @Test
        @DisplayName("空规则时抛异常")
        void emptyThrows() {
            assertThrows(IllegalStateException.class,
                    () -> DecisionChain.builder().build());
        }

        @Test
        @DisplayName("重复设置 otherwise 抛异常")
        void doubleOtherwiseThrows() {
            assertThrows(IllegalStateException.class, () ->
                    DecisionChain.<Integer, String>builder()
                            .otherwise(n -> "a")
                            .otherwise(n -> "b"));
        }
    }

    @Nested
    @DisplayName("静态工厂")
    class StaticFactory {

        @Test
        @DisplayName("of 创建单规则链")
        void of() {
            DecisionChain<Integer, String> chain = DecisionChain.of(n -> "结果:" + n);
            assertEquals("结果:42", chain.execute(42));
        }
    }

    @Nested
    @DisplayName("实际业务场景")
    class BusinessScenarios {

        static class Order {
            private final int amount;
            private final boolean vip;

            Order(int amount, boolean vip) {
                this.amount = amount;
                this.vip = vip;
            }

            int getAmount() { return amount; }
            boolean isVip() { return vip; }
        }

        @Test
        @DisplayName("订单路由——VIP 大额")
        void orderRoutingVipLarge() {
            DecisionChain<Order, String> chain = DecisionChain.<Order, String>builder()
                    .when(o -> o.isVip() && o.getAmount() > 1000, o -> "VIP大额订单")
                    .when(o -> o.isVip(),                          o -> "VIP普通订单")
                    .when(o -> o.getAmount() > 5000,               o -> "大额订单")
                    .otherwise(o -> "普通订单")
                    .build();

            assertEquals("VIP大额订单", chain.execute(new Order(2000, true)));
        }

        @Test
        @DisplayName("订单路由——非VIP大额")
        void orderRoutingLarge() {
            DecisionChain<Order, String> chain = DecisionChain.<Order, String>builder()
                    .when(o -> o.isVip() && o.getAmount() > 1000, o -> "VIP大额订单")
                    .when(o -> o.isVip(),                          o -> "VIP普通订单")
                    .when(o -> o.getAmount() > 5000,               o -> "大额订单")
                    .otherwise(o -> "普通订单")
                    .build();

            assertEquals("大额订单", chain.execute(new Order(6000, false)));
        }

        @Test
        @DisplayName("订单路由——默认")
        void orderRoutingDefault() {
            DecisionChain<Order, String> chain = DecisionChain.<Order, String>builder()
                    .when(o -> o.isVip() && o.getAmount() > 1000, o -> "VIP大额订单")
                    .when(o -> o.isVip(),                          o -> "VIP普通订单")
                    .when(o -> o.getAmount() > 5000,               o -> "大额订单")
                    .otherwise(o -> "普通订单")
                    .build();

            assertEquals("普通订单", chain.execute(new Order(100, false)));
        }
    }
}
