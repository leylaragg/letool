package com.github.leyland.letool.data.desensitize.rule;

/**
 * 身份证滑块脱敏规则
 * 保留前6位和后4位
 *
 * @author leyland
 * @date 2025-01-12
 */
public class IdCardSlideRule extends AbstractSlideDesensitizeRule {

    @Override
    public int leftKeep() {
        return 6;
    }

    @Override
    public int rightKeep() {
        return 4;
    }
}
