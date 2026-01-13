package com.github.leyland.letool.data.mapper.converter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日期字段转换器
 * 支持日期与字符串之间的转换
 *
 * @author leyland
 * @date 2025-01-12
 */
public class DateFieldConverter implements FieldConverter<Date, String> {

    private static final String[] DEFAULT_PATTERNS = {
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd",
        "HH:mm:ss"
    };

    private String pattern = "yyyy-MM-dd HH:mm:ss";

    public DateFieldConverter() {
    }

    public DateFieldConverter(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public String convert(Date sourceValue, Class<String> targetType) {
        if (sourceValue == null) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            return sdf.format(sourceValue);
        } catch (Exception e) {
            return sourceValue.toString();
        }
    }

    public Date convert(String sourceValue, Class<Date> targetType) {
        if (sourceValue == null || sourceValue.isEmpty()) {
            return null;
        }
        // 先使用指定的格式解析
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            return sdf.parse(sourceValue);
        } catch (ParseException e) {
            // 尝试使用默认格式
            for (String defaultPattern : DEFAULT_PATTERNS) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(defaultPattern);
                    return sdf.parse(sourceValue);
                } catch (ParseException ignored) {
                }
            }
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> sourceType, Class<?> targetType) {
        return (Date.class.isAssignableFrom(sourceType) && targetType == String.class) ||
               (sourceType == String.class && Date.class.isAssignableFrom(targetType));
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
