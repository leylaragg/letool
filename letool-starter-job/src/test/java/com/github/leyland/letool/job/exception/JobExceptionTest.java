package com.github.leyland.letool.job.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobException 任务异常测试")
class JobExceptionTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("(message, jobName) 构造应正确设置字段")
        void twoArgConstructorShouldSetFields() {
            JobException ex = new JobException("任务执行失败", "dailyReport");

            assertEquals("任务执行失败", ex.getMessage());
            assertEquals("dailyReport", ex.getJobName());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("(message, jobName, cause) 构造应正确设置所有字段")
        void threeArgConstructorShouldSetAllFields() {
            RuntimeException cause = new RuntimeException("root cause");
            JobException ex = new JobException("Cron解析失败", "syncJob", cause);

            assertEquals("Cron解析失败", ex.getMessage());
            assertEquals("syncJob", ex.getJobName());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("继承体系测试")
    class InheritanceTests {

        @Test
        @DisplayName("应继承 RuntimeException")
        void shouldExtendRuntimeException() {
            JobException ex = new JobException("test", "job1");
            assertTrue(ex instanceof RuntimeException);
        }
    }

    @Nested
    @DisplayName("getJobName 测试")
    class GetJobNameTests {

        @Test
        @DisplayName("应返回构造时传入的 jobName")
        void shouldReturnConstructedJobName() {
            JobException ex = new JobException("msg", "myJob");
            assertEquals("myJob", ex.getJobName());
        }

        @Test
        @DisplayName("不同异常实例的 jobName 应独立")
        void jobNamesShouldBeIndependent() {
            JobException ex1 = new JobException("msg1", "jobA");
            JobException ex2 = new JobException("msg2", "jobB");
            assertEquals("jobA", ex1.getJobName());
            assertEquals("jobB", ex2.getJobName());
        }
    }
}
