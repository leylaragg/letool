package io.github.leylaragg.letool.tool.config;

import io.github.leylaragg.letool.tool.http.HttpTemplate;
import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import io.github.leylaragg.letool.tool.spring.SpringUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 基础工具 Starter 自动配置。
 *
 * <p>该模块是轻量级工具基础层，只注册默认有用的 Spring 适配器 Bean。
 * Redis 基础设施由独立的 {@code letool-starter-redis} 模块负责。</p>
 */
@AutoConfiguration
public class LetoolToolAutoConfiguration {

    /**
     * 注册与底层实现无关的 JSON 扩展接口。
     *
     * <p>应用可以声明自定义 {@link JsonCodec} Bean 替换 Fastjson2。默认编解码器
     * 不可变，也不会修改 Fastjson2 全局状态。</p>
     *
     * @return 基于 Fastjson2 的默认 JSON 编解码器
     */
    @Bean
    @ConditionalOnMissingBean(JsonCodec.class)
    public JsonCodec jsonCodec() {
        return Fastjson2JsonCodec.createDefault();
    }

    /**
     * 在应用未自行提供时注册 Spring 应用上下文工具。
     *
     * @return Spring 应用上下文工具
     */
    @Bean
    @ConditionalOnMissingBean(SpringUtil.class)
    public SpringUtil springUtil() {
        return new SpringUtil();
    }

    /**
     * 注册使用 JDK 共享客户端的默认 HTTP 请求模板。
     *
     * <p>应用需要代理、自定义 TLS、认证器或不同请求边界时，可以自行声明 {@link HttpTemplate} Bean，
     * 默认实现会完整退让。</p>
     *
     * @return 可直接注入业务服务的线程安全 HTTP 模板
     */
    @Bean
    @ConditionalOnMissingBean(HttpTemplate.class)
    public HttpTemplate httpTemplate() {
        return new HttpTemplate();
    }

}
