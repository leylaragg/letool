package com.github.leyland.letool.data.mapper;

import com.github.leyland.letool.data.mapper.annotation.MapField;
import com.github.leyland.letool.data.mapper.annotation.MapIgnore;
import com.github.leyland.letool.data.mapper.context.MappingConfig;
import com.github.leyland.letool.data.mapper.context.MappingContext;
import com.github.leyland.letool.data.mapper.handler.FieldMappingHandler;
import com.github.leyland.letool.data.mapper.holder.HandlerHolder;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 对象映射工具类
 * 提供对象到对象映射功能
 *
 * @author leyland
 * @date 2025-01-12
 */
public final class ObjectMapperUtil {

    private static final MappingConfig DEFAULT_CONFIG = MappingConfig.defaultConfig();

    private ObjectMapperUtil() {
    }

    /**
     * 映射对象（使用默认配置）
     *
     * @param target 目标对象
     * @param sources 源对象数组
     * @param <T> 目标类型
     * @return 目标对象
     */
    public static <T> T map(T target, Object... sources) {
        return map(target, DEFAULT_CONFIG, sources);
    }

    /**
     * 映射对象（使用指定配置）
     *
     * @param target 目标对象
     * @param config 映射配置
     * @param sources 源对象数组
     * @param <T> 目标类型
     * @return 目标对象
     */
    public static <T> T map(T target, MappingConfig config, Object... sources) {
        if (target == null) {
            return null;
        }

        if (sources == null || sources.length == 0) {
            return target;
        }

        // 使用默认配置如果 config 为 null
        MappingConfig actualConfig = (config != null) ? config : DEFAULT_CONFIG;

        MappingContext context = new MappingContext(sources, target, actualConfig);
        processFields(context);

        return target;
    }

    /**
     * 创建新对象并映射
     *
     * @param targetClass 目标类
     * @param sources 源对象数组
     * @param <T> 目标类型
     * @return 映射后的新对象
     */
    public static <T> T mapToNew(Class<T> targetClass, Object... sources) {
        return mapToNew(targetClass, DEFAULT_CONFIG, sources);
    }

    /**
     * 创建新对象并映射（使用指定配置）
     *
     * @param targetClass 目标类
     * @param config 映射配置
     * @param sources 源对象数组
     * @param <T> 目标类型
     * @return 映射后的新对象
     */
    public static <T> T mapToNew(Class<T> targetClass, MappingConfig config, Object... sources) {
        if (targetClass == null) {
            return null;
        }

        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            return map(target, config, sources);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Target class " + targetClass.getName() + " does not have a no-arg constructor", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + targetClass.getName(), e);
        }
    }

    /**
     * 批量映射
     *
     * @param sourceList 源对象列表
     * @param targetClass 目标类
     * @param additionalSources 额外的源对象
     * @param <S> 源类型
     * @param <T> 目标类型
     * @return 映射后的目标列表
     */
     public static <S, T> List<T> mapList(List<S> sourceList, Class<T> targetClass, Object... additionalSources) {
        return mapList(sourceList, targetClass, DEFAULT_CONFIG, additionalSources);
    }

    /**
     * 批量映射（使用指定配置）
     *
     * @param sourceList 源对象列表
     * @param targetClass 目标类
     * @param config 映射配置
     * @param additionalSources 额外的源对象
     * @param <S> 源类型
     * @param <T> 目标类型
     * @return 映射后的目标列表
     */
    public static <S, T> List<T> mapList(List<S> sourceList, Class<T> targetClass, MappingConfig config, Object... additionalSources) {
        if (sourceList == null || sourceList.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<T> result = new java.util.ArrayList<>(sourceList.size());
        for (S source : sourceList) {
            Object[] sources = mergeSources(source, additionalSources);
            T target = mapToNew(targetClass, config, sources);
            result.add(target);
        }
        return result;
    }

    /**
     * 处理字段
     */
    private static void processFields(MappingContext context) {
        if (context == null || context.getTarget() == null) {
            return;
        }

        Class<?> targetClass = context.getTarget().getClass();
        Field[] fields = getAllFields(targetClass);

        if (fields == null || fields.length == 0) {
            return;
        }

        for (Field targetField : fields) {
            if (targetField != null) {
                processField(context, targetField);
            }
        }
    }

    /**
     * 处理单个字段
     */
    private static void processField(MappingContext context, Field targetField) {
        // 检查是否忽略
        if (shouldIgnoreField(context, targetField)) {
            return;
        }

        context.setCurrentField(targetField);

        // 获取源值
        Object sourceValue = getSourceValue(context, targetField);
        if (sourceValue == null && context.getConfig() != null && context.getConfig().isIgnoreNull()) {
            return;
        }

        // 应用处理器链
        Object processedValue = applyHandlers(context, targetField, sourceValue);

        // 设置目标字段值
        setFieldValue(context, targetField, processedValue);
    }

    /**
     * 判断是否应该忽略字段
     */
    private static boolean shouldIgnoreField(MappingContext context, Field targetField) {
        if (targetField == null) {
            return true;
        }

        MapIgnore mapIgnore = targetField.getAnnotation(MapIgnore.class);
        if (mapIgnore != null) {
            // TODO: 可以根据 condition 表达式进行条件判断
            return true;
        }
        return false;
    }

    /**
     * 获取源值
     */
    private static Object getSourceValue(MappingContext context, Field targetField) {
        if (context == null || targetField == null) {
            return null;
        }

        MapField mapField = targetField.getAnnotation(MapField.class);

        if (mapField != null) {
            // 使用注解配置的源
            int sourceIndex = mapField.sourceIndex();
            Object source = context.getSource(sourceIndex);
            String sourcePath = mapField.sourcePath();
            String fieldName = sourcePath == null || sourcePath.isEmpty() ? targetField.getName() : sourcePath;
            return getFieldValue(source, fieldName);
        } else {
            // 自动匹配
            String fieldName = targetField.getName();
            Object[] sources = context.getSources();
            if (sources == null) {
                return null;
            }
            for (Object source : sources) {
                Object value = getFieldValue(source, fieldName);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 应用处理器链
     */
    private static Object applyHandlers(MappingContext context, Field targetField, Object sourceValue) {
        if (context == null || context.getConfig() == null || !context.getConfig().isEnableHandlerChain()) {
            return sourceValue;
        }

        Object value = sourceValue;
        List<FieldMappingHandler> handlers = HandlerHolder.getAllHandlers();

        if (handlers == null || handlers.isEmpty()) {
            return value;
        }

        for (FieldMappingHandler handler : handlers) {
            if (handler != null && handler.supports(context, targetField)) {
                value = handler.handle(context, targetField, value);
            }
        }

        return value;
    }

    /**
     * 设置字段值
     */
    private static void setFieldValue(MappingContext context, Field targetField, Object value) {
        if (context == null || targetField == null) {
            return;
        }

        try {
            targetField.setAccessible(true);
            targetField.set(context.getTarget(), value);
        } catch (IllegalAccessException e) {
            // 忽略设置失败
        } catch (IllegalArgumentException e) {
            // 类型不匹配，尝试类型转换
            try {
                Object convertedValue = TypeConverterUtil.convert(value, targetField.getType());
                targetField.set(context.getTarget(), convertedValue);
            } catch (Exception ex) {
                // 忽略转换失败
            }
        }
    }

    /**
     * 获取字段值
     */
    private static Object getFieldValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        try {
            Field field = getField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return null;
    }

    /**
     * 获取字段（包括父类）
     */
    private static Field getField(Class<?> clazz, String fieldName) {
        if (clazz == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取所有字段（包括父类）
     */
    private static Field[] getAllFields(Class<?> clazz) {
        if (clazz == null) {
            return new Field[0];
        }

        List<Field> fields = new java.util.ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            if (declaredFields != null) {
                fields.addAll(java.util.Arrays.asList(declaredFields));
            }
            current = current.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * 合并源对象
     */
    private static Object[] mergeSources(Object primarySource, Object... additionalSources) {
        if (primarySource == null && (additionalSources == null || additionalSources.length == 0)) {
            return new Object[0];
        }

        if (additionalSources == null || additionalSources.length == 0) {
            return new Object[]{primarySource};
        }

        Object[] sources = new Object[additionalSources.length + 1];
        sources[0] = primarySource;
        System.arraycopy(additionalSources, 0, sources, 1, additionalSources.length);
        return sources;
    }
}
