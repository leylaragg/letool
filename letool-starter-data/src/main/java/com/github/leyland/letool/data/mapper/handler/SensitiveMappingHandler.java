package com.github.leyland.letool.data.mapper.handler;

import com.github.leyland.letool.data.desensitize.Sensitive;
import com.github.leyland.letool.data.desensitize.SensitiveUtil;
import com.github.leyland.letool.data.mapper.context.MappingContext;

import java.lang.reflect.Field;

/**
 * 脱敏映射处理器
 * 处理字段值的脱敏转换
 *
 * @author leyland
 * @date 2025-01-12
 */
public class SensitiveMappingHandler implements FieldMappingHandler {

    @Override
    public Object handle(MappingContext context, Field targetField, Object sourceValue) {
        if (sourceValue == null || !(sourceValue instanceof String)) {
            return sourceValue;
        }

        Sensitive sensitive = targetField.getAnnotation(Sensitive.class);
        if (sensitive == null) {
            return sourceValue;
        }

        return SensitiveUtil.desensitize((String) sourceValue, sensitive);
    }

    @Override
    public boolean supports(MappingContext context, Field targetField) {
        return targetField.isAnnotationPresent(Sensitive.class);
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getName() {
        return "SensitiveMappingHandler";
    }
}
