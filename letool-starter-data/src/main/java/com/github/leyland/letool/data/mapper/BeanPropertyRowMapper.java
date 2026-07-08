package com.github.leyland.letool.data.mapper;

import com.github.leyland.letool.data.annotation.Column;
import com.github.leyland.letool.data.annotation.Id;
import com.github.leyland.letool.data.annotation.Transient;
import com.github.leyland.letool.tool.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于反射的 Bean 属性行映射器，实现 Spring {@link RowMapper} 接口。
 *
 * <p>将 JDBC 结果集的每一行映射为指定类型的 Java Bean 实例。
 * 支持以下映射规则（优先级从高到低）：</p>
 * <ol>
 *   <li>{@link Column @Column} 注解显式指定的列名</li>
 *   <li>自动驼峰转下划线（{@code userName} → {@code user_name}），需开启 {@code autoCamelCase}</li>
 *   <li>字段名直接作为列名</li>
 * </ol>
 *
 * <p>自动跳过以下字段：</p>
 * <ul>
 *   <li>标记 {@link Transient @Transient} 的字段</li>
 *   <li>静态字段（static）</li>
 * </ul>
 *
 * <p>支持的类型转换：</p>
 * <ul>
 *   <li>字符串 → 枚举（通过 {@code Enum.valueOf}）</li>
 *   <li>Number → Long / Integer</li>
 *   <li>Number → Boolean（非零为 true）</li>
 * </ul>
 *
 * @param <T> 目标 Bean 类型
 * @author leyland
 * @since 2.0.0
 */
public class BeanPropertyRowMapper<T> implements RowMapper<T> {

    private static final Logger log = LoggerFactory.getLogger(BeanPropertyRowMapper.class);

    /** 目标映射类 */
    private final Class<T> mappedClass;

    /** 是否启用自动驼峰转换 */
    private final boolean autoCamelCase;

    /** 数据库列名（小写）→ Java Field 的映射缓存，构造时一次性构建 */
    private final Map<String, Field> columnToField;

    /**
     * 构造行映射器并构建列 → 字段映射缓存。
     *
     * @param mappedClass   目标实体类
     * @param autoCamelCase 是否自动将数据库下划线列名转换为 Java 驼峰字段名
     */
    public BeanPropertyRowMapper(Class<T> mappedClass, boolean autoCamelCase) {
        this.mappedClass = mappedClass;
        this.autoCamelCase = autoCamelCase;
        this.columnToField = buildColumnMapping();
    }

    /**
     * 构建数据库列名到 Java 字段的映射。
     *
     * <p>遍历目标类的所有声明字段，按优先级确定每个字段对应的列名。
     * 结果集 key 为列名的全小写形式，确保 SQL 结果集大小写不敏感匹配。</p>
     *
     * @return 列名（小写）到 Field 的有序映射
     */
    private Map<String, Field> buildColumnMapping() {
        Map<String, Field> mapping = new LinkedHashMap<>();
        // 遍历整个类层次结构，包含父类中使用 @Column 注解的继承字段
        Class<?> clazz = mappedClass;
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Transient.class)) continue;
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                // 子类字段覆盖父类同名字段
                if (mapping.containsKey(field.getName().toLowerCase())) continue;

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
            clazz = clazz.getSuperclass();
        }
        return mapping;
    }

    /**
     * 将结果集当前行映射为 Java Bean 实例。
     *
     * <p>通过反射创建实例，遍历结果集的所有列，在 columnToField 中查找对应字段并赋值。
     * 如果列名带下划线未匹配，还会尝试去掉所有下划线后再次匹配。</p>
     *
     * @param rs     结果集，已定位到当前行
     * @param rowNum 当前行号（从 0 开始）
     * @return 映射后的 Bean 实例
     * @throws SQLException 如果映射过程中发生反射异常
     */
    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            Constructor<T> constructor = mappedClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = constructor.newInstance();
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

    /**
     * 将数据库返回的原始值转换为目标字段类型。
     *
     * <p>处理类型不匹配的常见场景：String→Enum、Number→Long/Integer、
     * Number→Boolean。如果值已是目标类型则直接返回。</p>
     *
     * @param value      数据库返回的原始值
     * @param targetType 目标字段的 Java 类型
     * @return 转换后的值
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) return value;
        if (targetType.isEnum() && value instanceof String) {
            return Enum.valueOf((Class<? extends Enum>) targetType, (String) value);
        }
        // 处理基本类型及其包装类型，确保 DB 驱动返回的 Number 可赋值给原始类型字段
        if ((targetType == Long.class || targetType == long.class) && value instanceof Number) {
            return ((Number) value).longValue();
        }
        if ((targetType == Integer.class || targetType == int.class) && value instanceof Number) {
            return ((Number) value).intValue();
        }
        if ((targetType == Short.class || targetType == short.class) && value instanceof Number) {
            return ((Number) value).shortValue();
        }
        if ((targetType == Byte.class || targetType == byte.class) && value instanceof Number) {
            return ((Number) value).byteValue();
        }
        if ((targetType == Double.class || targetType == double.class) && value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if ((targetType == Float.class || targetType == float.class) && value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if ((targetType == Boolean.class || targetType == boolean.class) && value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return value;
    }
}
