package com.github.leyland.letool.sample;

import com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * letool sample 的组合启动测试。
 *
 * <p>sample 模块会同时引入多个 starter，这个测试用于尽早发现自动配置之间的
 * Bean 冲突、条件装配缺口和基础依赖缺失问题。</p>
 */
@SpringBootTest
class SampleStarterContextTest {

    /** Spring 应用上下文，用于检查多 starter 组合后的 Bean 契约。 */
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 验证示例应用可以完整启动，且 Letool 执行器不会覆盖 Spring Boot 基础执行器。
     *
     * <p>Spring Boot 3.5 只注册 {@code applicationTaskExecutor}，不再提供
     * {@code taskExecutor} 别名。</p>
     */
    @Test
    void sampleStartersShouldLoadTogether() {
        assertThat(applicationContext.containsBean("applicationTaskExecutor")).isTrue();
        assertThat(applicationContext.containsBean("taskExecutor")).isFalse();
        assertThat(applicationContext.containsBean("letoolTaskExecutor")).isTrue();
        assertThat(applicationContext.containsBean("letoolIoExecutor")).isTrue();
        assertThat(applicationContext.getBeansOfType(RateLimitAspect.class)).hasSize(1);
        assertThat(applicationContext.getBean("letoolTaskExecutor"))
                .isNotSameAs(applicationContext.getBean("applicationTaskExecutor"));
    }
}
