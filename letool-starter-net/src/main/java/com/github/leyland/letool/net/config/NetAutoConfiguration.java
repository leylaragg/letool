package com.github.leyland.letool.net.config;

import com.github.leyland.letool.net.gateway.NetGateway;
import com.github.leyland.letool.net.http.NetHttpTemplate;
import com.github.leyland.letool.net.tcp.TcpClient;
import com.github.leyland.letool.net.tcp.TcpLongClient;
import com.github.leyland.letool.net.tcp.TcpShortClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 网络通信模块自动配置 —— 激活 {@code letool-starter-net} 的所有核心 Bean.
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li>{@link NetGateway} —— 企业级统一网络网关，管理 TCP 和 HTTP 路由（始终注册）</li>
 *   <li>{@link NetHttpTemplate} —— HTTP 客户端模板（有条件激活，需要 HttpClient 依赖）</li>
 * </ul>
 *
 * <h3>配置绑定</h3>
 * <p>通过 {@link EnableConfigurationProperties} 激活 {@link NetProperties}，
 * 所有 {@code letool.net.*} 前缀的 YAML 配置将被自动绑定.</p>
 *
 * <h3>禁用方式</h3>
 * <p>设置 {@code letool.net.gateway.enabled=false} 可禁用网关功能.</p>
 *
 * <p><b>注意：</b>TCP 客户端的创建由 {@link NetGateway} 按需管理，不由自动配置直接注入.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(NetProperties.class)
public class NetAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NetAutoConfiguration.class);

    // ======================== NetGateway Bean ========================

    /**
     * 创建 {@link NetGateway} Bean —— 统一网络网关.
     *
     * <p>通过 {@code letool.net.gateway.enabled} 控制，默认为启用.</p>
     *
     * <p>网关负责：</p>
     * <ul>
     *   <li>管理 TCP 和 HTTP 路由的生命周期</li>
     *   <li>为每条路由创建对应的 TCP 客户端或 HTTP 模板</li>
     *   <li>提供统一的 {@code send(routeId, message)} 入口</li>
     * </ul>
     *
     * @param properties 网络配置属性
     * @return NetGateway 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.net.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
    public NetGateway netGateway(NetProperties properties) {
        log.info("Initializing NetGateway...");
        NetGateway gateway = new NetGateway(properties);

        // 从配置文件中加载 YAML 声明的路由（通过 NetProperties.getGateway().getRoutes() 注入）
        // 注意：YAML 路由的自动注册需在 Bean 后置处理中完成，或由 NetGateway 内部在初始化时读取配置
        log.info("NetGateway initialized successfully");
        return gateway;
    }

    // ======================== NetHttpTemplate Bean ========================

    /**
     * 创建 {@link NetHttpTemplate} Bean —— HTTP 客户端模板.
     *
     * <p>该 Bean 有条件激活：仅在 classpath 中存在 HTTP 客户端依赖时创建.
     * 目前使用 JDK 内置的 {@link java.net.HttpURLConnection}，无需外部依赖.</p>
     *
     * <p>当项目中已定义同名 Bean 或设置 {@code letool.net.http.enabled=false} 时，此方法不执行.</p>
     *
     * @param properties 网络配置属性
     * @return NetHttpTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.net.http", name = "enabled", havingValue = "true", matchIfMissing = true)
    public NetHttpTemplate netHttpTemplate(NetProperties properties) {
        log.info("Initializing NetHttpTemplate...");
        return new NetHttpTemplate(properties);
    }
}
