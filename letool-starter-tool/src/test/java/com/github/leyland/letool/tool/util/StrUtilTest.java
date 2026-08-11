package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.value.ValueErrorCode;
import com.github.leyland.letool.tool.value.ValueOperationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字符串工具关键边界测试。
 */
class StrUtilTest {

    /**
     * 验证空参数数组和 Unicode 空白字符不会破坏判空契约。
     */
    @Test
    void shouldHandleNullVarargsAndUnicodeBlank() {
        assertTrue(StrUtil.hasEmpty((CharSequence[]) null));
        assertTrue(StrUtil.hasBlank((CharSequence[]) null));
        assertTrue(StrUtil.isBlank("\u00A0"));
        assertFalse(StrUtil.hasBlank());
    }

    /**
     * 验证参数不足时保留未匹配占位符，避免格式化结果丢失上下文。
     */
    @Test
    void shouldPreserveUnmatchedPlaceholders() {
        assertEquals("a,{},{}", StrUtil.format("{},{},{}", "a"));
    }

    /**
     * 验证命名转换不受系统语言环境影响，并正确处理缩写和连续下划线。
     */
    @Test
    void shouldConvertNamesUsingRootLocaleAndAcronymBoundaries() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("userId", StrUtil.toCamelCase("USER_ID"));
            assertEquals("userName", StrUtil.toCamelCase("USER__NAME"));
            assertEquals("url_value", StrUtil.toSnakeCase("URLValue"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * 验证截取操作按 Unicode 码点工作，不会产生残缺代理字符。
     */
    @Test
    void shouldSliceUnicodeByCodePoint() {
        String value = "A😀B";

        assertEquals("A😀", StrUtil.left(value, 2));
        assertEquals("😀B", StrUtil.right(value, 2));
        assertEquals("A😀...", StrUtil.truncate(value, 2));
        assertEquals("😀abc", StrUtil.uncapitalize("😀abc"));
    }

    /**
     * 验证非法长度和分隔符使用统一、稳定的基础值异常。
     */
    @Test
    void shouldRejectInvalidLengthAndDelimiterWithStableError() {
        ValueOperationException lengthException = assertThrows(
                ValueOperationException.class,
                () -> StrUtil.truncate("value", -1)
        );
        ValueOperationException delimiterException = assertThrows(
                ValueOperationException.class,
                () -> StrUtil.join(List.of("a", "b"), null)
        );
        ValueOperationException emptyDelimiterException = assertThrows(
                ValueOperationException.class,
                () -> StrUtil.split("a,b", "")
        );

        assertEquals(ValueErrorCode.INVALID_ARGUMENT, lengthException.getErrorCode());
        assertEquals(ValueErrorCode.INVALID_ARGUMENT, delimiterException.getErrorCode());
        assertEquals(ValueErrorCode.INVALID_ARGUMENT, emptyDelimiterException.getErrorCode());
    }
}
