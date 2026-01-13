package com.github.leyland.letool.data.desensitize.handler;

/**
 * 中文名脱敏处理器
 * 单名只显示最后一个汉字，双名显示第一个和最后一个汉字
 *
 * @author leyland
 * @date 2025-01-12
 */
public class ChineseNameHandler implements SimpleDesensitizeHandler {

    @Override
    public String mask(String origin) {
        if (origin == null || origin.isEmpty()) {
            return origin;
        }

        int length = origin.length();
        if (length == 1) {
            return origin;
        } else if (length == 2) {
            return "*" + origin.charAt(1);
        } else {
            return origin.charAt(0) + "*" + origin.charAt(length - 1);
        }
    }
}
