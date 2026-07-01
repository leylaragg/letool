package com.github.leyland.letool.job.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobDefinition 任务定义测试")
class JobDefinitionTest {

    @Nested
    @DisplayName("Builder 基础构建测试")
    class BuilderBasicTests {

        @Test
        @DisplayName("最小配置应构建成功")
        void minimalConfigShouldBuild() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("testJob")
                    .handler(ctx -> {})
                    .build();

            assertEquals("testJob", job.getJobName());
            assertNotNull(job.getHandler());
        }

        @Test
        @DisplayName("完整配置应正确设置所有字段")
        void fullConfigShouldSetAllFields() {
            JobHandler handler = ctx -> {};
            JobDefinition job = JobDefinition.builder()
                    .jobName("dailyReport")
                    .cron("0 0 6 * * ?")
                    .description("每日报表")
                    .shardTotal(4)
                    .shardIndex(2)
                    .maxRetries(5)
                    .backoffMs(2000)
                    .backoffMultiplier(3.0)
                    .handler(handler)
                    .param("type", "daily")
                    .param("target", "all")
                    .build();

            assertEquals("dailyReport", job.getJobName());
            assertEquals("0 0 6 * * ?", job.getCron());
            assertEquals("每日报表", job.getDescription());
            assertEquals(4, job.getShardTotal());
            assertEquals(2, job.getShardIndex());
            assertEquals(5, job.getMaxRetries());
            assertEquals(2000, job.getBackoffMs());
            assertEquals(3.0, job.getBackoffMultiplier());
            assertSame(handler, job.getHandler());
            assertEquals("daily", job.getParams().get("type"));
            assertEquals("all", job.getParams().get("target"));
        }
    }

    @Nested
    @DisplayName("Builder 默认值测试")
    class BuilderDefaultsTests {

        @Test
        @DisplayName("未设置 shardTotal 时应默认为 1")
        void defaultShardTotalShouldBeOne() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).build();
            assertEquals(1, job.getShardTotal());
        }

        @Test
        @DisplayName("未设置 shardIndex 时应默认为 0")
        void defaultShardIndexShouldBeZero() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).build();
            assertEquals(0, job.getShardIndex());
        }

        @Test
        @DisplayName("未设置 maxRetries 时应默认为 3")
        void defaultMaxRetriesShouldBeThree() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).build();
            assertEquals(3, job.getMaxRetries());
        }

        @Test
        @DisplayName("未设置 backoffMs 时应默认为 1000")
        void defaultBackoffMsShouldBe1000() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).build();
            assertEquals(1000, job.getBackoffMs());
        }

        @Test
        @DisplayName("未设置 backoffMultiplier 时应默认为 2.0")
        void defaultBackoffMultiplierShouldBe2() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).build();
            assertEquals(2.0, job.getBackoffMultiplier());
        }

        @Test
        @DisplayName("未设置 description 时应默认为空字符串")
        void defaultDescriptionShouldBeEmpty() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).build();
            assertEquals("", job.getDescription());
        }

        @Test
        @DisplayName("未设置 params 时应返回空 Map")
        void defaultParamsShouldBeEmpty() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).build();
            assertTrue(job.getParams().isEmpty());
        }

        @Test
        @DisplayName("shardTotal <= 0 时应设为 1")
        void shardTotalShouldBeAtLeastOne() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {}).shardTotal(0).build();
            assertEquals(1, job.getShardTotal());
        }
    }

    @Nested
    @DisplayName("Builder 校验测试")
    class BuilderValidationTests {

        @Test
        @DisplayName("jobName 为 null 时应抛异常")
        void nullJobNameShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    JobDefinition.builder().handler(ctx -> {}).build());
        }

        @Test
        @DisplayName("jobName 为空字符串时应抛异常")
        void emptyJobNameShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    JobDefinition.builder().jobName("   ").handler(ctx -> {}).build());
        }

        @Test
        @DisplayName("handler 为 null 时应抛异常")
        void nullHandlerShouldThrow() {
            assertThrows(IllegalArgumentException.class, () ->
                    JobDefinition.builder().jobName("test").build());
        }

        @Test
        @DisplayName("异常消息应包含有意义的提示")
        void exceptionMessageShouldBeMeaningful() {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    JobDefinition.builder().build());
            assertTrue(ex.getMessage().contains("jobName"));

            Exception ex2 = assertThrows(IllegalArgumentException.class, () ->
                    JobDefinition.builder().jobName("test").build());
            assertTrue(ex2.getMessage().contains("handler"));
        }
    }

    @Nested
    @DisplayName("params 测试")
    class ParamsTests {

        @Test
        @DisplayName("param() 应逐个添加入参")
        void paramShouldAddIndividually() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {})
                    .param("k1", "v1")
                    .param("k2", 100)
                    .build();

            assertEquals("v1", job.getParams().get("k1"));
            assertEquals(100, job.getParams().get("k2"));
            assertEquals(2, job.getParams().size());
        }

        @Test
        @DisplayName("params(Map) 应批量添加入参")
        void paramsShouldBatchAdd() {
            Map<String, Object> map = new HashMap<>();
            map.put("a", 1);
            map.put("b", 2);

            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {})
                    .params(map)
                    .build();

            assertEquals(2, job.getParams().size());
            assertEquals(1, job.getParams().get("a"));
        }

        @Test
        @DisplayName("返回的 params 应不可修改")
        void returnedParamsShouldBeUnmodifiable() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("test").handler(ctx -> {})
                    .param("key", "val")
                    .build();

            Map<String, Object> params = job.getParams();
            assertThrows(UnsupportedOperationException.class, () -> params.put("new", "val"));
        }
    }

    @Nested
    @DisplayName("Builder 链式调用测试")
    class BuilderChainingTests {

        @Test
        @DisplayName("所有 Builder 方法应返回自身")
        void allBuilderMethodsShouldReturnThis() {
            JobDefinition.Builder builder = JobDefinition.builder();
            assertSame(builder, builder.jobName("test"));
            assertSame(builder, builder.cron("0 0 * * * ?"));
            assertSame(builder, builder.description("desc"));
            assertSame(builder, builder.shardTotal(3));
            assertSame(builder, builder.shardIndex(1));
            assertSame(builder, builder.maxRetries(5));
            assertSame(builder, builder.backoffMs(500));
            assertSame(builder, builder.backoffMultiplier(1.5));
            assertSame(builder, builder.handler(ctx -> {}));
            assertSame(builder, builder.param("k", "v"));
        }
    }

    @Nested
    @DisplayName("toString 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            JobDefinition job = JobDefinition.builder()
                    .jobName("syncJob")
                    .cron("0 */5 * * * ?")
                    .description("数据同步")
                    .handler(ctx -> {})
                    .build();

            String str = job.toString();
            assertTrue(str.contains("syncJob"));
            assertTrue(str.contains("0 */5 * * * ?"));
            assertTrue(str.contains("数据同步"));
        }
    }
}
