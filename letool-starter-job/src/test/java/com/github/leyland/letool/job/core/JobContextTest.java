package com.github.leyland.letool.job.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobContext 任务执行上下文测试")
class JobContextTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应正确初始化所有字段")
        void shouldInitializeAllFields() {
            Map<String, Object> params = new HashMap<>();
            params.put("batchSize", 100);

            JobContext ctx = new JobContext("syncJob", 2, 4, params);

            assertEquals("syncJob", ctx.getJobName());
            assertNotNull(ctx.getExecutionId());
            assertFalse(ctx.getExecutionId().isEmpty());
            assertEquals(2, ctx.getShardIndex());
            assertEquals(4, ctx.getShardTotal());
            assertNotNull(ctx.getStartTime());
        }

        @Test
        @DisplayName("两次创建的 executionId 应不同")
        void eachContextShouldHaveUniqueExecutionId() {
            JobContext ctx1 = new JobContext("job1", 0, 1, null);
            JobContext ctx2 = new JobContext("job1", 0, 1, null);
            assertNotEquals(ctx1.getExecutionId(), ctx2.getExecutionId());
        }

        @Test
        @DisplayName("shardTotal <= 0 时应设为 1")
        void shardTotalShouldBeAtLeastOne() {
            JobContext ctx = new JobContext("job1", 0, 0, null);
            assertEquals(1, ctx.getShardTotal());
        }

        @Test
        @DisplayName("shardTotal 为负数时应设为 1")
        void negativeShardTotalShouldBeOne() {
            JobContext ctx = new JobContext("job1", 0, -5, null);
            assertEquals(1, ctx.getShardTotal());
        }

        @Test
        @DisplayName("params 为 null 时应初始化为空 Map")
        void nullParamsShouldBeEmptyMap() {
            JobContext ctx = new JobContext("job1", 0, 1, null);
            assertNotNull(ctx.getParams());
            assertTrue(ctx.getParams().isEmpty());
        }

        @Test
        @DisplayName("params 应不可修改")
        void paramsShouldBeUnmodifiable() {
            Map<String, Object> params = new HashMap<>();
            params.put("key", "value");
            JobContext ctx = new JobContext("job1", 0, 1, params);

            Map<String, Object> returnedParams = ctx.getParams();
            assertThrows(UnsupportedOperationException.class, () -> returnedParams.put("new", "val"));
        }

        @Test
        @DisplayName("getParams 返回的 Map 应不可修改")
        void getParamsShouldBeUnmodifiable() {
            Map<String, Object> original = new HashMap<>();
            original.put("key", "value");
            JobContext ctx = new JobContext("job1", 0, 1, original);

            Map<String, Object> returnedParams = ctx.getParams();
            assertThrows(UnsupportedOperationException.class, () -> returnedParams.put("new", "val"));
        }
    }

    @Nested
    @DisplayName("getParam(key) 测试")
    class GetParamByKeyTests {

        @Test
        @DisplayName("存在的 key 应返回对应值")
        void shouldReturnValueForExistingKey() {
            Map<String, Object> params = new HashMap<>();
            params.put("name", "test");
            JobContext ctx = new JobContext("job1", 0, 1, params);

            assertEquals("test", ctx.getParam("name"));
        }

        @Test
        @DisplayName("不存在的 key 应返回 null")
        void shouldReturnNullForMissingKey() {
            JobContext ctx = new JobContext("job1", 0, 1, null);
            assertNull(ctx.getParam("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getParam(key, class) 测试")
    class GetParamWithTypeTests {

        @Test
        @DisplayName("存在的 key 且类型匹配应返回 Optional.of(value)")
        void shouldReturnOptionalWithValue() {
            Map<String, Object> params = new HashMap<>();
            params.put("count", 100);
            params.put("name", "test");
            JobContext ctx = new JobContext("job1", 0, 1, params);

            Optional<Integer> count = ctx.getParam("count", Integer.class);
            assertTrue(count.isPresent());
            assertEquals(100, count.get());

            Optional<String> name = ctx.getParam("name", String.class);
            assertTrue(name.isPresent());
            assertEquals("test", name.get());
        }

        @Test
        @DisplayName("不存在的 key 应返回 Optional.empty()")
        void shouldReturnEmptyForMissingKey() {
            JobContext ctx = new JobContext("job1", 0, 1, null);
            Optional<String> result = ctx.getParam("missing", String.class);
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("类型参数仅作为文档提示，不执行运行时类型检查")
        void classParameterIsDocumentationOnly() {
            Map<String, Object> params = new HashMap<>();
            params.put("value", "string_not_integer");
            JobContext ctx = new JobContext("job1", 0, 1, params);

            // getParam(key, class) 的泛型擦除后不会做运行时类型检查
            Optional<Integer> result = ctx.getParam("value", Integer.class);
            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("toString 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            JobContext ctx = new JobContext("syncJob", 1, 4, null);
            String str = ctx.toString();
            assertTrue(str.contains("syncJob"));
            assertTrue(str.contains("shardIndex=1"));
            assertTrue(str.contains("shardTotal=4"));
        }
    }
}
