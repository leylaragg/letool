package com.github.leyland.data.desensitize.rule;

/**
 * 抽象滑块脱敏规则
 * 提供默认实现
 *
 * @author leyland
 * @date 2025-01-12
 */
public abstract class AbstractSlideDesensitizeRule implements SlideDesensitizeRule {

    @Override
    public String maskChar() {
        return "*";
    }

    @Override
    public boolean reverse() {
        return false;
    }
}
