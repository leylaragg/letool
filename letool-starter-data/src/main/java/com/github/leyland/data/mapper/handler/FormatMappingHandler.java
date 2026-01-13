package com.github.leyland.data.mapper.handler;

import com.github.leyland.data.mapper.annotation.MapField;
import com.github.leyland.data.mapper.context.MappingContext;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 格式化映射处理器
 * 处理日期、数字等类型的格式化
 *
 * @author leyland
 * @date 2025-01-12
 */
public class FormatMappingHandler implements FieldMappingHandler {

    @Override
    public Object handle(MappingContext context, Field targetField, Object sourceValue) {
        if (sourceValue == null) {
            return handleNullValue(targetField);
        }

        MapField mapField = targetField.getAnnotation(MapField.class);
        if (mapField == null) {
            return sourceValue;
        }

        String format = mapField.format();
        if (format == null || format.isEmpty()) {
            format = context.getConfig().getDefaultDateFormat();
        }

        // 如果目标是 String 类型，进行格式化
        if (targetField.getType() == String.class) {
            return formatValue(sourceValue, format);
        }

        return sourceValue;
    }

    @Override
    public boolean supports(MappingContext context, Field targetField) {
        MapField mapField = targetField.getAnnotation(MapField.class);
        if (mapField == null) {
            return false;
        }
        return !mapField.format().isEmpty() || targetField.getType() == String.class;
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public String getName() {
        return "FormatMappingHandler";
    }

    /**
     * 格式化值
     */
    private Object formatValue(Object value, String format) {
        try {
            if (value instanceof Date) {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
                return sdf.format((Date) value);
            }
            if (value instanceof Number) {
                DecimalFormat df = new DecimalFormat(format);
                return df.format(value);
            }
        } catch (Exception e) {
            // 格式化失败，返回原始值
        }
        return value.toString();
    }

    /**
     * 处理空值
     */
    private Object handleNullValue(Field targetField) {
        MapField mapField = targetField.getAnnotation(MapField.class);
        if (mapField != null && !mapField.defaultValue().isEmpty()) {
            return mapField.defaultValue();
        }
        return null;
    }
}
