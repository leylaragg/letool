package com.github.leyland.data.desensitize;

/**
 * 滑块脱敏规则接口
 * 定义滑块脱敏的规则
 *
 * @author leyland
 * @date 2025-01-12
 */
public interface SlideDesensitizeRule {

    /**
     * 左边保留字符数
     *
     * @return 保留数量
     */
    int leftKeep();

    /**
     * 右边保留字符数
     *
     * @return 保留数量
     */
    int rightKeep();

    /**
     * 掩码字符
     *
     * @return 掩码字符
     */
    default String maskChar() {
        return "*";
    }

    /**
     * 是否反转
     *
     * @return 是否反转
     */
    default boolean reverse() {
        return false;
    }
}
