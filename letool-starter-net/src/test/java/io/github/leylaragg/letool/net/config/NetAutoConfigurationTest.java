package io.github.leylaragg.letool.net.config;

import io.github.leylaragg.letool.net.tcp.NetRuntime;
import io.github.leylaragg.letool.net.tcp.TcpClientFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NetAutoConfiguration} 自动配置契约测试。
 */
class NetAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NetAutoConfiguration.class));

    /**
     * 关闭测试自行提供且不应由 Letool 越权关闭的线程组。
     */
    @AfterEach
    void closeUserEventLoopGroup() {
        NioEventLoopGroup eventLoopGroup = UserRuntimeConfiguration.eventLoopGroup;
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS)
                    .syncUninterruptibly();
            UserRuntimeConfiguration.eventLoopGroup = null;
        }
    }

    /**
     * 验证默认注册惰性运行时和客户端工厂。
     */
    @Test
    void shouldCreateLazyTcpInfrastructureByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(NetProperties.class);
            assertThat(context).hasSingleBean(NetRuntime.class);
            assertThat(context).hasSingleBean(TcpClientFactory.class);
            assertThat(context.getBean(NetRuntime.class).isInitialized()).isFalse();
        });
    }

    /**
     * 验证关闭 TCP 功能后不会创建线程运行时和客户端工厂。
     */
    @Test
    void shouldNotCreateTcpInfrastructureWhenDisabled() {
        contextRunner
                .withPropertyValues("letool.net.tcp.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(NetProperties.class);
                    assertThat(context).doesNotHaveBean(NetRuntime.class);
                    assertThat(context).doesNotHaveBean(TcpClientFactory.class);
                });
    }

    /**
     * 验证用户提供运行时后自动配置会退让，客户端工厂复用该运行时。
     */
    @Test
    void shouldBackOffToUserRuntime() {
        contextRunner
                .withUserConfiguration(UserRuntimeConfiguration.class)
                .run(context -> {
                    NetRuntime runtime = context.getBean(NetRuntime.class);
                    TcpClientFactory factory = context.getBean(TcpClientFactory.class);

                    assertThat(runtime).isSameAs(context.getBean("customRuntime"));
                    assertThat(factory.runtime()).isSameAs(runtime);
                });
    }

    /**
     * 模拟业务应用接管 Netty 线程资源。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserRuntimeConfiguration {

        private static NioEventLoopGroup eventLoopGroup;

        /**
         * 创建由测试代码自行管理底层线程组的自定义运行时。
         *
         * @return 自定义网络运行时
         */
        @Bean(destroyMethod = "close")
        NetRuntime customRuntime() {
            eventLoopGroup = new NioEventLoopGroup(1);
            return new NetRuntime(eventLoopGroup);
        }
    }
}
