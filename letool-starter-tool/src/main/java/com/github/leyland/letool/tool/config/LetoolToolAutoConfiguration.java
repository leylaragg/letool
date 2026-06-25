package com.github.leyland.letool.tool.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * letool-starter-tool 自动配置——激活整个 tool 模块的 Spring 组件.
 *
 * <h3>自动注册的 Bean</h3>
 * <ul>
 *   <li>{@link com.github.leyland.letool.tool.spring.SpringUtil SpringUtil} — ApplicationContext 持有者</li>
 *   <li>{@link com.github.leyland.letool.tool.redis.RedisUtil RedisUtil} — Redis 操作工具（有条件激活，需引入 Redis 依赖）</li>
 * </ul>
 *
 * <h3>激活方式</h3>
 * <p>引入 {@code letool-starter-tool} 依赖后自动激活（通过
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}）.</p>
 *
 * <p>注意：使用 Spring Boot 3.x 的 {@link AutoConfiguration} 注解替代旧的 {@code @Configuration}，
 * 并通过 {@code AutoConfiguration.imports} 文件注册（替代旧的 {@code spring.factories}）.</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.github.leyland.letool.tool")
public class LetoolToolAutoConfiguration {
}
