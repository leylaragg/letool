package com.github.leyland.letool.job.quartz;

import com.github.leyland.letool.job.annotation.LetoolJob;
import com.github.leyland.letool.job.core.DefaultJobHandlerRegistry;
import com.github.leyland.letool.job.core.JobContext;
import com.github.leyland.letool.job.core.JobDefinition;
import com.github.leyland.letool.job.exception.JobException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LetoolJobRegistrar} 注解任务扫描测试。
 */
class LetoolJobRegistrarTest {

    /**
     * 验证代理 Bean 的类注解和上下文处理方法能够被发现并注册。
     *
     * @throws Exception 处理器调用失败时抛出
     */
    @Test
    void shouldDiscoverProxiedTaskAndInvokeContextMethod() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ContextTask target = new ContextTask();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        beanFactory.registerSingleton("contextTask", proxyFactory.getProxy());
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        List<JobDefinition> definitions = new ArrayList<>();
        LetoolJobRegistrar registrar = new LetoolJobRegistrar(
                beanFactory, registry, (definition, beanName) -> definitions.add(definition));

        registrar.afterSingletonsInstantiated();
        registry.getRequired("context-task").execute(null);

        assertThat(definitions).singleElement().satisfies(definition -> {
            assertThat(definition.getJobName()).isEqualTo("context-task");
            assertThat(definition.getShardTotal()).isEqualTo(2);
        });
        assertThat(target.invocations).hasValue(1);
    }

    /**
     * 验证零参数处理方法同样可以作为便利任务入口。
     *
     * @throws Exception 处理器调用失败时抛出
     */
    @Test
    void shouldInvokeNoArgumentHandler() throws Exception {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        NoArgumentTask task = new NoArgumentTask();
        beanFactory.registerSingleton("noArgumentTask", task);
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        LetoolJobRegistrar registrar = new LetoolJobRegistrar(beanFactory, registry, (definition, beanName) -> { });

        registrar.afterSingletonsInstantiated();
        registry.getRequired("no-argument").execute(null);

        assertThat(task.invocations).hasValue(1);
    }

    /**
     * 验证多个处理方法和非法返回值会在启动阶段失败。
     */
    @Test
    void shouldRejectAmbiguousOrInvalidHandlerMethods() {
        assertInvalidTask(new MultipleHandlerTask());
        assertInvalidTask(new ReturningTask());
    }

    /**
     * 验证非法注解定义会转换为统一任务定义错误码，并保留校验异常。
     */
    @Test
    void shouldWrapInvalidAnnotationDefinitionWithStableCode() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("invalidDefinitionTask", new InvalidDefinitionTask());
        LetoolJobRegistrar registrar = new LetoolJobRegistrar(
                beanFactory, new DefaultJobHandlerRegistry(), (definition, beanName) -> { });

        assertThatThrownBy(registrar::afterSingletonsInstantiated)
                .isInstanceOf(JobException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .extracting("code")
                .isEqualTo("JOB_001");
    }

    /**
     * 注册非法任务并验证稳定错误码。
     *
     * @param task 非法任务实例
     */
    private void assertInvalidTask(Object task) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("invalidTask", task);
        LetoolJobRegistrar registrar = new LetoolJobRegistrar(
                beanFactory, new DefaultJobHandlerRegistry(), (definition, beanName) -> { });

        assertThatThrownBy(registrar::afterSingletonsInstantiated)
                .isInstanceOf(JobException.class)
                .extracting("code")
                .isEqualTo("JOB_004");
    }

    /** 有效的代理任务。 */
    @LetoolJob(name = "context-task", cron = "0/30 * * * * ?", shardTotal = 2)
    static class ContextTask {
        private final AtomicInteger invocations = new AtomicInteger();

        /** @param context 任务上下文 */
        @com.github.leyland.letool.job.annotation.JobHandler
        public void execute(JobContext context) {
            invocations.incrementAndGet();
        }
    }

    /** 有效的零参数任务。 */
    @LetoolJob(name = "no-argument")
    static class NoArgumentTask {
        private final AtomicInteger invocations = new AtomicInteger();

        /** 执行任务。 */
        @com.github.leyland.letool.job.annotation.JobHandler
        public void execute() {
            invocations.incrementAndGet();
        }
    }

    /** 包含多个处理方法的非法任务。 */
    @LetoolJob(name = "multiple")
    static class MultipleHandlerTask {
        /** 第一个处理方法。 */
        @com.github.leyland.letool.job.annotation.JobHandler
        public void first() { }
        /** 第二个处理方法。 */
        @com.github.leyland.letool.job.annotation.JobHandler
        public void second() { }
    }

    /** 返回非 void 的非法任务。 */
    @LetoolJob(name = "returning")
    static class ReturningTask {
        /** @return 非法返回值 */
        @com.github.leyland.letool.job.annotation.JobHandler
        public String execute() { return "invalid"; }
    }

    /** 包含非法 Cron 的任务。 */
    @LetoolJob(name = "invalid-definition", cron = "invalid")
    static class InvalidDefinitionTask {
        /** 执行任务。 */
        @com.github.leyland.letool.job.annotation.JobHandler
        public void execute() { }
    }
}
