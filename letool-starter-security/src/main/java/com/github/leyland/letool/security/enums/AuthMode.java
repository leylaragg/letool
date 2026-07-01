package com.github.leyland.letool.security.enums;

/**
 * 认证模式枚举。
 *
 * <ul>
 *   <li>{@link #JWT} — 无状态 JWT 认证，Token 自包含用户信息</li>
 *   <li>{@link #JWT_REDIS} — JWT + Redis 存储，支持主动失效和分布式会话</li>
 *   <li>{@link #SESSION} — 传统 Session 会话认证</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public enum AuthMode {
    /** 无状态 JWT 认证 */
    JWT,
    /** JWT + Redis 存储，Token 可主动失效 */
    JWT_REDIS,
    /** 传统 Session 认证 */
    SESSION
}
