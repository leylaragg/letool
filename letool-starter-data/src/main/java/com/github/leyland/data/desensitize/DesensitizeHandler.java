package com.github.leyland.data.desensitize;

/**
 * 脱敏处理器接口
 * 所有脱敏处理器都需要实现此接口
 *
 * @author leyland
 * @date 2025-01-12
 */
public interface DesensitizeHandler {

    /**
     * 脱敏处理
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    String mask(String origin);

    /**
     * 判断是否支持该类型
     *
     * @param clazz 目标类型
     * @return 是否支持
     */
    default boolean supports(Class<?> clazz) {
        return true;
    }
}
