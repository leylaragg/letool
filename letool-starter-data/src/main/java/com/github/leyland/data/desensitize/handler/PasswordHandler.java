package com.github.leyland.data.desensitize.handler;

import com.github.leyland.data.desensitize.SimpleDesensitizeHandler;

/**
 * 密码脱敏处理器
 * 全部替换为 ******（6个星号）
 *
 * @author leyland
 * @date 2025-01-12
 */
public class PasswordHandler implements SimpleDesensitizeHandler {

    @Override
    public String mask(String origin) {
        if (origin == null) {
            return null;
        }
        return "******";
    }
}
