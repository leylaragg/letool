package com.github.leyland.data.desensitize.rule;

/**
 * 地址滑块脱敏规则
 * 保留前6位
 *
 * @author leyland
 * @date 2025-01-12
 */
public class AddressSlideRule extends AbstractSlideDesensitizeRule {

    @Override
    public int leftKeep() {
        return 6;
    }

    @Override
    public int rightKeep() {
        return 0;
    }
}
