package io.github.leylaragg.letool.net.config;

import io.github.leylaragg.letool.net.tcp.NetRuntime;
import io.github.leylaragg.letool.net.tcp.TcpClientFactory;
import io.netty.channel.Channel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * TCP 客户端共享基础设施自动配置。
 *
 * <p>只注册惰性 {@link NetRuntime} 和轻量 {@link TcpClientFactory}，不会自动连接任何
 * 远程服务。业务应用提供同类型 Bean 时自动配置会主动退让。</p>
 */
@AutoConfiguration
@ConditionalOnClass(Channel.class)
@EnableConfigurationProperties(NetProperties.class)
public class NetAutoConfiguration {

    /**
     * 创建由 Spring 管理生命周期的惰性 Netty 运行时。
     *
     * @param properties 网络模块配置
     * @return 共享网络运行时
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.net.tcp",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public NetRuntime netRuntime(NetProperties properties) {
        NetProperties.Tcp tcp = properties.getTcp();
        return new NetRuntime(
                tcp.getEventLoopThreads(),
                tcp.getShutdownQuietPeriod(),
                tcp.getShutdownTimeout());
    }

    /**
     * 创建复用共享运行时的 TCP 客户端工厂。
     *
     * @param runtime 共享网络运行时
     * @return TCP 客户端工厂
     */
    @Bean
    @ConditionalOnBean(NetRuntime.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "letool.net.tcp",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public TcpClientFactory tcpClientFactory(NetRuntime runtime) {
        return new TcpClientFactory(runtime);
    }
}
