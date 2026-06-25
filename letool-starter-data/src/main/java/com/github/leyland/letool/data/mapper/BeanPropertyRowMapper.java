package com.github.leyland.letool.data.mapper;

import com.github.leyland.letool.data.annotation.Column;
import com.github.leyland.letool.data.annotation.Id;
import com.github.leyland.letool.data.annotation.Transient;
import com.github.leyland.letool.tool.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class BeanPropertyRowMapper<T> implements RowMapper<T> {

    private static final Logger log = LoggerFactory.getLogger(BeanPropertyRowMapper.class);

    private final Class<T> mappedClass;
    private final boolean autoCamelCase;
    private final Map<String, Field> columnToField;

    public BeanPropertyRowMapper(Class<T> mappedClass, boolean autoCamelCase) {
        this.mappedClass = mappedClass;
        this.autoCamelCase = autoCamelCase;
        this.columnToField = buildColumnMapping();
    }

    private Map<String, Field> buildColumnMapping() {
        Map<String, Field> mapping = new LinkedHashMap<>();
        for (Field field : mappedClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Transient.class)) continue;
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

            field.setAccessible(true);
            String columnName;
            Column colAnn = field.getAnnotation(Column.class);
            if (colAnn != null) {
                columnName = colAnn.value();
            } else if (autoCamelCase) {
                columnName = StrUtil.toSnakeCase(field.getName());
            } else {
                columnName = field.getName();
            }
            mapping.put(columnName.toLowerCase(), field);
        }
        return mapping;
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            T instance = mappedClass.getDeclaredConstructor().newInstance();
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                String columnName = meta.getColumnLabel(i).toLowerCase();
                Field field = columnToField.get(columnName);
                if (field == null) {
                    columnName = columnName.replace("_", "");
                    field = columnToField.get(columnName);
                }
                if (field != null) {
                    Object value = rs.getObject(i);
                    if (value != null) {
                        field.set(instance, convertValue(value, field.getType()));
                    }
                }
            }
            return instance;
        } catch (Exception e) {
            throw new SQLException("Failed to map row to " + mappedClass.getName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) return value;
        if (targetType.isEnum() && value instanceof String) {
            return Enum.valueOf((Class<? extends Enum>) targetType, (String) value);
        }
        if (targetType == Long.class && value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (targetType == Integer.class && value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (targetType == Boolean.class && value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return value;
    }
}
