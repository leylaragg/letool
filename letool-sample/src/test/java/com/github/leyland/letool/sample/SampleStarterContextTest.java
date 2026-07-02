package com.github.leyland.letool.sample;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * letool sample 的组合启动测试。
 *
 * <p>sample 模块会同时引入多个 starter，这个测试用于尽早发现自动配置之间的
 * Bean 冲突、条件装配缺口和基础依赖缺失问题。</p>
 */
@SpringBootTest
class SampleStarterContextTest {

    /**
     * 验证 sample 应用上下文可以完整启动。
     */
    @Test
    void sampleStartersShouldLoadTogether() {
        // Context loading is the assertion.
    }
}
