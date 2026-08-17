package io.github.leylaragg.letool.log.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MethodLog} 方法日志注解契约测试。
 */
class MethodLogTest {

    /**
     * 默认配置只应记录方法执行结果和耗时，不应采集可能包含敏感信息的入参与出参。
     *
     * @throws NoSuchMethodException 测试方法不存在时抛出
     */
    @Test
    void shouldDisableArgumentAndResultLoggingByDefault() throws NoSuchMethodException {
        Method method = SampleService.class.getDeclaredMethod("execute", String.class);
        MethodLog methodLog = method.getAnnotation(MethodLog.class);

        assertThat(methodLog.logArgs()).isFalse();
        assertThat(methodLog.logResult()).isFalse();
        assertThat(methodLog.logException()).isTrue();
    }

    /**
     * 入参应提供独立的最大记录长度，避免与返回值截断策略互相影响。
     *
     * @throws NoSuchMethodException 注解属性不存在时抛出
     */
    @Test
    void shouldProvideIndependentArgumentLengthLimit() throws NoSuchMethodException {
        Method maxArgsLength = MethodLog.class.getDeclaredMethod("maxArgsLength");

        assertThat(maxArgsLength.getDefaultValue()).isEqualTo(500);
    }

    /**
     * 提供使用默认配置的方法日志注解样例。
     */
    private static final class SampleService {

        /**
         * 返回原始参数。
         *
         * @param value 测试参数
         * @return 原始参数
         */
        @MethodLog
        private String execute(String value) {
            return value;
        }
    }
}
