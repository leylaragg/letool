package com.github.leyland.letool.security.jwt.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Security JWT 主自动配置
 * <p>
 * 在 Spring Security 自动配置之前执行
 *
 * @author Rungo
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@AutoConfigureBefore(SecurityAutoConfiguration.class)
public class SecurityJwtAutoConfiguration {
}