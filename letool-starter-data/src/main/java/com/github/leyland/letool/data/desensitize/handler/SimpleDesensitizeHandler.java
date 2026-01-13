package com.github.leyland.letool.data.desensitize.handler;

/**
 * 简单脱敏处理器接口
 * 用于只需要输入原始值即可完成脱敏的简单场景
 *
 * @author leyland
 * @date 2025-01-12
 */
public interface SimpleDesensitizeHandler extends DesensitizeHandler {

    /**
     * 脱敏处理（无需额外参数）
     *
     * @param origin 原始值
     * @return 脱敏后的值
     */
    String mask(String origin);

}
