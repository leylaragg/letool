package com.github.leyland.letool.data.mapper;

import com.github.leyland.letool.data.mapper.annotation.MapField;
import com.github.leyland.letool.data.mapper.annotation.MapIgnore;
import com.github.leyland.letool.data.mapper.context.MappingConfig;
import com.github.leyland.letool.data.mapper.context.MappingContext;
import com.github.leyland.letool.data.mapper.handler.FieldMappingHandler;
import com.github.leyland.letool.data.mapper.holder.HandlerHolder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
     * 转换模式枚举
     */
    public enum ConvertMode {
        /**
         * 直接映射模式（使用反射进行字段映射）
         */
        DIRECT,
        /**
         * FastJson转换模式（使用FastJson进行序列化和反序列化）
         */
        FASTJSON
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
     * 转换对象（支持多种转换模式）
     *
     * @param source 源对象（可以是Object、Map、JSON字符串等）
     * @param targetClass 目标类
     * @param mode 转换模式
     * @param <T> 目标类型
     * @return 转换后的对象
     */
    public static <T> T convert(Object source, Class<T> targetClass, ConvertMode mode) {
        if (source == null || targetClass == null) {
            return null;
        }

        if (mode == null) {
            mode = ConvertMode.DIRECT;
        }

        return ConvertStrategyFactory.getStrategy(mode).convert(source, targetClass);
    }

    /**
     * 转换对象（使用默认转换模式：DIRECT）
     *
     * @param source 源对象
     * @param targetClass 目标类
     * @param <T> 目标类型
     * @return 转换后的对象
     */
    public static <T> T convert(Object source, Class<T> targetClass) {
        return convert(source, targetClass, ConvertMode.DIRECT);
    }

    /**
     * 批量转换对象（支持多种转换模式）
     *
     * @param sourceList 源对象列表（可以是List<Object>、List<Map>、JSON数组字符串等）
     * @param targetClass 目标类
     * @param mode 转换模式
     * @param <T> 目标类型
     * @return 转换后的对象列表
     */
    public static <T> List<T> convertList(Object sourceList, Class<T> targetClass, ConvertMode mode) {
        if (sourceList == null || targetClass == null) {
            return new ArrayList<>();
        }

        if (mode == null) {
            mode = ConvertMode.DIRECT;
        }

        return ConvertStrategyFactory.getStrategy(mode).convertList(sourceList, targetClass);
    }

    /**
     * 批量转换对象（使用默认转换模式：DIRECT）
     *
     * @param sourceList 源对象列表
     * @param targetClass 目标类
     * @param <T> 目标类型
     * @return 转换后的对象列表
     */
    public static <T> List<T> convertList(Object sourceList, Class<T> targetClass) {
        return convertList(sourceList, targetClass, ConvertMode.DIRECT);
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 源对象
     * @param mode 转换模式
     * @return JSON字符串
     */
    public static String toJson(Object obj, ConvertMode mode) {
        if (obj == null) {
            return null;
        }

        if (mode == null) {
            mode = ConvertMode.DIRECT;
        }

        return ConvertStrategyFactory.getStrategy(mode).toJson(obj);
    }

    /**
     * 将对象转换为JSON字符串（使用默认转换模式：DIRECT）
     *
     * @param obj 源对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        return toJson(obj, ConvertMode.DIRECT);
    }

    /**
     * 将对象转换为Map
     *
     * @param obj 源对象
     * @param mode 转换模式
     * @return Map
     */
    public static Map<String, Object> toMap(Object obj, ConvertMode mode) {
        if (obj == null) {
            return null;
        }

        if (mode == null) {
            mode = ConvertMode.DIRECT;
        }

        return ConvertStrategyFactory.getStrategy(mode).toMap(obj);
    }

    /**
     * 将对象转换为Map（使用默认转换模式：DIRECT）
     *
     * @param obj 源对象
     * @return Map
     */
    public static Map<String, Object> toMap(Object obj) {
        return toMap(obj, ConvertMode.DIRECT);
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
            try {
                Object convertedValue = TypeConverterUtil.convert(value, targetField.getType());
                targetField.set(context.getTarget(), convertedValue);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to convert value: " + e.getMessage());
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

        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            return map.get(fieldName);
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

        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            if (declaredFields != null) {
                fields.addAll(Arrays.asList(declaredFields));
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

    /**
     * 转换策略接口
     */
    private interface ConvertStrategy {
        <T> T convert(Object source, Class<T> targetClass);
        <T> List<T> convertList(Object sourceList, Class<T> targetClass);
        String toJson(Object obj);
        @SuppressWarnings("unchecked")
        Map<String, Object> toMap(Object obj);
    }

    /**
     * 直接映射策略（使用反射）
     */
    private static class DirectConvertStrategy implements ConvertStrategy {
        @Override
        public <T> T convert(Object source, Class<T> targetClass) {
            if (source == null || targetClass == null) {
                return null;
            }

            try {
                T target = targetClass.getDeclaredConstructor().newInstance();
                return mapFromMapToTarget(source, target, DEFAULT_CONFIG);
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert using direct mapping: " + e.getMessage(), e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> convertList(Object sourceList, Class<T> targetClass) {
            if (sourceList == null || targetClass == null) {
                return new ArrayList<>();
            }

            if (!(sourceList instanceof List)) {
                throw new IllegalArgumentException("Source must be a List");
            }

            List<?> list = (List<?>) sourceList;
            List<T> result = new ArrayList<>(list.size());
            for (Object item : list) {
                T target = convert(item, targetClass);
                result.add(target);
            }
            return result;
        }

        @Override
        public String toJson(Object obj) {
            if (obj == null) {
                return null;
            }
            return obj.toString();
        }

        @Override
        public Map<String, Object> toMap(Object obj) {
            if (obj == null) {
                return null;
            }
            if (obj instanceof Map) {
                return (Map<String, Object>) obj;
            }
            throw new UnsupportedOperationException("Direct mapping strategy does not support object to Map conversion. Use FASTJSON mode instead.");
        }

        @SuppressWarnings("unchecked")
        private <T> T mapFromMapToTarget(Object source, T target, MappingConfig config) {
            if (source == null || target == null) {
                return target;
            }

            MappingConfig actualConfig = (config != null) ? config : DEFAULT_CONFIG;
            MappingContext context = new MappingContext(new Object[]{source}, target, actualConfig);
            processFields(context);
            return target;
        }
    }

    /**
     * FastJson转换策略
     */
    private static class FastJsonConvertStrategy implements ConvertStrategy {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T convert(Object source, Class<T> targetClass) {
            if (source == null || targetClass == null) {
                return null;
            }

            try {
                if (source instanceof String) {
                    return com.alibaba.fastjson2.JSON.parseObject((String) source, targetClass);
                } else {
                    return com.alibaba.fastjson2.JSON.parseObject(com.alibaba.fastjson2.JSON.toJSONString(source), targetClass);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert using FastJson: " + e.getMessage(), e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> convertList(Object sourceList, Class<T> targetClass) {
            if (sourceList == null || targetClass == null) {
                return new ArrayList<>();
            }

            try {
                if (sourceList instanceof String) {
                    return com.alibaba.fastjson2.JSON.parseArray((String) sourceList, targetClass);
                } else {
                    return com.alibaba.fastjson2.JSON.parseArray(com.alibaba.fastjson2.JSON.toJSONString(sourceList), targetClass);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert list using FastJson: " + e.getMessage(), e);
            }
        }

        @Override
        public String toJson(Object obj) {
            if (obj == null) {
                return null;
            }
            return com.alibaba.fastjson2.JSON.toJSONString(obj);
        }

        @Override
        public Map<String, Object> toMap(Object obj) {
            if (obj == null) {
                return null;
            }
            String jsonStr = com.alibaba.fastjson2.JSON.toJSONString(obj);
            return com.alibaba.fastjson2.JSON.parseObject(jsonStr, Map.class);
        }
    }

    /**
     * 转换策略工厂
     */
    private static class ConvertStrategyFactory {
        private static final Map<ConvertMode, ConvertStrategy> STRATEGY_MAP = new java.util.HashMap<>();

        static {
            STRATEGY_MAP.put(ConvertMode.DIRECT, new DirectConvertStrategy());
            STRATEGY_MAP.put(ConvertMode.FASTJSON, new FastJsonConvertStrategy());
        }

        public static ConvertStrategy getStrategy(ConvertMode mode) {
            if (mode == null) {
                return STRATEGY_MAP.get(ConvertMode.DIRECT);
            }
            return STRATEGY_MAP.get(mode);
        }
    }
}
