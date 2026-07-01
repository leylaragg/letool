package com.github.leyland.letool.job.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobResult 任务执行结果测试")
class JobResultTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(executionId, jobName) 构造应初始化为 RUNNING 状态")
        void shouldInitializeAsRunning() {
            JobResult result = new JobResult("exec-001", "testJob");
            assertEquals("exec-001", result.getExecutionId());
            assertEquals("testJob", result.getJobName());
            assertEquals(JobStatus.RUNNING, result.getStatus());
            assertNotNull(result.getStartTime());
            assertEquals(0, result.getDurationMs());
        }

        @Test
        @DisplayName("从 JobContext 构造应复制 jobName 和 executionId")
        void fromContextShouldCopyFields() {
            JobContext ctx = new JobContext("myJob", 0, 1, null);
            JobResult result = new JobResult(ctx);
            assertEquals(ctx.getJobName(), result.getJobName());
            assertEquals(ctx.getExecutionId(), result.getExecutionId());
            assertEquals(JobStatus.RUNNING, result.getStatus());
        }
    }

    @Nested
    @DisplayName("success() 方法测试")
    class SuccessTests {

        @Test
        @DisplayName("应设置状态为 SUCCESS 并记录结束时间")
        void shouldSetSuccessAndRecordEndTime() {
            JobResult result = new JobResult("e1", "job1");
            result.success("处理完成，共100条");

            assertEquals(JobStatus.SUCCESS, result.getStatus());
            assertEquals("处理完成，共100条", result.getResult());
            assertNotNull(result.getEndTime());
            assertTrue(result.getDurationMs() >= 0);
            assertTrue(result.isSuccess());
            assertFalse(result.isFailed());
        }

        @Test
        @DisplayName("success() 返回自身以支持链式调用")
        void shouldReturnThisForChaining() {
            JobResult result = new JobResult("e1", "job1");
            assertSame(result, result.success("ok"));
        }
    }

    @Nested
    @DisplayName("fail() 方法测试")
    class FailTests {

        @Test
        @DisplayName("应设置状态为 FAIL 并记录错误信息")
        void shouldSetFailAndRecordError() {
            JobResult result = new JobResult("e1", "job1");
            result.fail("数据库连接失败", 2);

            assertEquals(JobStatus.FAIL, result.getStatus());
            assertEquals("数据库连接失败", result.getErrorMessage());
            assertEquals(2, result.getRetryCount());
            assertNotNull(result.getEndTime());
            assertTrue(result.getDurationMs() >= 0);
            assertTrue(result.isFailed());
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("fail() 返回自身以支持链式调用")
        void shouldReturnThisForChaining() {
            JobResult result = new JobResult("e1", "job1");
            assertSame(result, result.fail("error", 0));
        }
    }

    @Nested
    @DisplayName("timeout() 方法测试")
    class TimeoutTests {

        @Test
        @DisplayName("应设置状态为 TIMEOUT 并记录错误信息")
        void shouldSetTimeoutAndRecordError() {
            JobResult result = new JobResult("e1", "job1");
            result.timeout("任务执行超过30秒");

            assertEquals(JobStatus.TIMEOUT, result.getStatus());
            assertEquals("任务执行超过30秒", result.getErrorMessage());
            assertNotNull(result.getEndTime());
            assertTrue(result.getDurationMs() >= 0);
        }

        @Test
        @DisplayName("timeout() 返回自身以支持链式调用")
        void shouldReturnThisForChaining() {
            JobResult result = new JobResult("e1", "job1");
            assertSame(result, result.timeout("timeout"));
        }
    }

    @Nested
    @DisplayName("isSuccess/isFailed 测试")
    class StatusCheckTests {

        @Test
        @DisplayName("新创建的 RUNNING 状态不应视为成功或失败")
        void runningShouldNotBeSuccessOrFailed() {
            JobResult result = new JobResult("e1", "job1");
            assertFalse(result.isSuccess());
            assertFalse(result.isFailed());
        }

        @Test
        @DisplayName("SUCCESS 状态下 isSuccess=true, isFailed=false")
        void successState() {
            JobResult result = new JobResult("e1", "job1").success("ok");
            assertTrue(result.isSuccess());
            assertFalse(result.isFailed());
        }

        @Test
        @DisplayName("FAIL 状态下 isFailed=true, isSuccess=false")
        void failState() {
            JobResult result = new JobResult("e1", "job1").fail("err", 0);
            assertTrue(result.isFailed());
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("TIMEOUT 状态下 isSuccess=false, isFailed=false")
        void timeoutState() {
            JobResult result = new JobResult("e1", "job1").timeout("timeout");
            assertFalse(result.isSuccess());
            assertFalse(result.isFailed());
        }
    }

    @Nested
    @DisplayName("toString 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            JobResult result = new JobResult("exec-abc", "dailyReport")
                    .success("done");
            String str = result.toString();
            assertTrue(str.contains("exec-abc"));
            assertTrue(str.contains("dailyReport"));
            assertTrue(str.contains("SUCCESS"));
        }
    }
}
