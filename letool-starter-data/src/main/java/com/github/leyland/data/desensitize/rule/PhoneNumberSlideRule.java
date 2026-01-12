package com.github.leyland.data.desensitize.rule;

/**
 * 手机号滑块脱敏规则
 * 保留前3位和后4位
 *
 * @author leyland
 * @date 2025-01-12
 */
public class PhoneNumberSlideRule extends AbstractSlideDesensitizeRule {

    @Override
    public int leftKeep() {
        return 3;
    }

    @Override
    public int rightKeep() {
        return 4;
    }
}
