package com.github.leyland.letool.security.enums;

/**
 * 认证模式枚举。
 *
 * <p>当前模块只公开已经实现并由 Spring Security Resource Server 验证的 JWT 模式。
 * Redis 主动失效和 Session 认证应由独立实现提供，避免配置存在但运行时仍走 JWT。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum AuthMode {
    /** 无状态 JWT 认证 */
    JWT
}
