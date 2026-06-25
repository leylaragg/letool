package com.github.leyland.letool.web.xss;

import org.springframework.web.util.HtmlUtils;

/**
 * XSS 清理器 —— 对输入字符串进行 HTML 转义，防止 XSS 攻击.
 */
public final class XssCleaner {

    private XssCleaner() {}

    /**
     * 清理字符串中的 XSS 风险字符（HTML 转义）.
     *
     * @param value 原始输入
     * @return 清理后的字符串，{@code null} 返回 {@code null}
     */
    public static String clean(String value) {
        if (value == null) return null;
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
