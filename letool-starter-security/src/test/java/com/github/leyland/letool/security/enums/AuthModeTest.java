package com.github.leyland.letool.security.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AuthMode} 对外认证模式契约测试。
 */
class AuthModeTest {

    /**
     * 验证模块只公开已经实现的 JWT 模式，避免配置成功但运行时仍走其他认证逻辑。
     */
    @Test
    void shouldOnlyExposeImplementedJwtMode() {
        assertArrayEquals(new AuthMode[]{AuthMode.JWT}, AuthMode.values());
    }

    /**
     * 验证 JWT 配置值可按枚举标准规则解析。
     */
    @Test
    void shouldParseJwtMode() {
        assertEquals(AuthMode.JWT, AuthMode.valueOf("JWT"));
    }

    /**
     * 验证未实现的认证模式不会再被配置绑定接受。
     */
    @Test
    void shouldRejectUnsupportedModes() {
        assertThrows(IllegalArgumentException.class, () -> AuthMode.valueOf("JWT_REDIS"));
        assertThrows(IllegalArgumentException.class, () -> AuthMode.valueOf("SESSION"));
    }
}
