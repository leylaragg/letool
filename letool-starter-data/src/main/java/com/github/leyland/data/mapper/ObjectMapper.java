package com.github.leyland.data.mapper;

import com.github.leyland.data.desensitize.Sensitive;
import com.github.leyland.data.desensitize.SensitiveUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 对象映射器
 * 支持从多个源对象映射到目标VO对象
 *
 * @author leyland
 * @date 2025-01-08
 */
public class ObjectMapper {

    /**
     * 全局映射监听器
     */
    private static MapperListener globalListener;

    /**
     * 设置全局映射监听器
     *
     * @param listener 监听器
     */
    public static void setGlobalListener(MapperListener listener) {
        globalListener = listener;
    }

    /**
     * 将多个源对象的值映射到目标VO对象
     *
     * @param target 目标VO对象
     * @param sources 源对象数组
     * @param <T> 目标类型
     * @return 填充后的目标对象
     */
    @SafeVarargs
    public static <T> T map(T target, Object... sources) {
        return map(target, null, sources);
    }

    /**
     * 将多个源对象的值映射到目标VO对象（带监听器）
     *
     * @param target 目标VO对象
     * @param listener 映射监听器
     * @param sources 源对象数组
     * @param <T> 目标类型
     * @return 填充后的目标对象
     */
    @SafeVarargs
    public static <T> T map(T target, MapperListener listener, Object... sources) {
        if (target == null || sources == null || sources.length == 0) {
            return target;
        }

        MapperListener actualListener = listener != null ? listener : globalListener;

        // 映射前回调
        if (actualListener != null) {
            actualListener.beforeMapping(target, sources);
        }

        Class<?> targetClass = target.getClass();
        // 使用缓存的字段信息
        Field[] fields = FieldInfoCache.getAllFields(targetClass);

        for (Field targetField : fields) {
            MapFrom mapFrom = targetField.getAnnotation(MapFrom.class);
            if (mapFrom != null) {
                processFieldWithAnnotation(target, targetField, mapFrom, sources, actualListener);
            } else {
                // 没有注解时，尝试按字段名自动匹配
                processFieldWithoutAnnotation(target, targetField, sources, actualListener);
            }
        }

        // 映射后回调
        if (actualListener != null) {
            actualListener.afterMapping(target, sources);
        }

        return target;
    }

    /**
     * 创建目标对象的新实例并映射值
     *
     * @param targetClass 目标类
     * @param sources 源对象数组
     * @param <T> 目标类型
     * @return 映射后的新对象
     */
    @SafeVarargs
    public static <T> T mapToNew(Class<T> targetClass, Object... sources) {
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            return map(target, sources);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + targetClass.getName(), e);
        }
    }

    /**
     * 批量映射：将源列表映射为目标列表
     *
     * @param sourceList 源对象列表
     * @param targetClass 目标类
     * @param sources 额外的源对象（固定参数）
     * @param <S> 源类型
     * @param <T> 目标类型
     * @return 映射后的目标列表
     */
    @SafeVarargs
    public static <S, T> List<T> mapList(List<S> sourceList, Class<T> targetClass, Object... sources) {
        if (sourceList == null || sourceList.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<T> result = new java.util.ArrayList<>(sourceList.size());
        for (S source : sourceList) {
            // 将当前源对象和额外源对象合并
            Object[] allSources = new Object[sources.length + 1];
            allSources[0] = source;
            System.arraycopy(sources, 0, allSources, 1, sources.length);

            T target = mapToNew(targetClass, allSources);
            result.add(target);
        }
        return result;
    }

    /**
     * 批量映射：将源数组映射为目标列表
     *
     * @param sourceArray 源对象数组
     * @param targetClass 目标类
     * @param sources 额外的源对象（固定参数）
     * @param <S> 源类型
     * @param <T> 目标类型
     * @return 映射后的目标列表
     */
    @SafeVarargs
    public static <S, T> List<T> mapArray(S[] sourceArray, Class<T> targetClass, Object... sources) {
        if (sourceArray == null || sourceArray.length == 0) {
            return new java.util.ArrayList<>();
        }

        List<T> result = new java.util.ArrayList<>(sourceArray.length);
        for (S source : sourceArray) {
            // 将当前源对象和额外源对象合并
            Object[] allSources = new Object[sources.length + 1];
            allSources[0] = source;
            System.arraycopy(sources, 0, allSources, 1, sources.length);

            T target = mapToNew(targetClass, allSources);
            result.add(target);
        }
        return result;
    }

    /**
     * 批量映射：将源Set映射为目标列表
     *
     * @param sourceSet 源对象Set
     * @param targetClass 目标类
     * @param sources 额外的源对象（固定参数）
     * @param <S> 源类型
     * @param <T> 目标类型
     * @return 映射后的目标列表
     */
    @SafeVarargs
    public static <S, T> List<T> mapSet(Set<S> sourceSet, Class<T> targetClass, Object... sources) {
        if (sourceSet == null || sourceSet.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<T> result = new java.util.ArrayList<>(sourceSet.size());
        for (S source : sourceSet) {
            // 将当前源对象和额外源对象合并
            Object[] allSources = new Object[sources.length + 1];
            allSources[0] = source;
            System.arraycopy(sources, 0, allSources, 1, sources.length);

            T target = mapToNew(targetClass, allSources);
            result.add(target);
        }
        return result;
    }

    /**
     * 处理带有 @MapFrom 注解的字段
     */
    private static void processFieldWithAnnotation(Object target, Field targetField, MapFrom mapFrom, Object[] sources, MapperListener listener) {
        int sourceIndex = mapFrom.sourceIndex();
        if (sourceIndex >= sources.length || sources[sourceIndex] == null) {
            return;
        }

        Object source = sources[sourceIndex];
        String sourcePath = mapFrom.value();

        try {
            Object sourceValue;
            if (sourcePath.isEmpty()) {
                // 如果没有指定路径，使用字段名
                sourceValue = getFieldValue(source, targetField.getName());
            } else {
                // 使用指定的路径获取值
                sourceValue = getNestedFieldValue(source, sourcePath);
            }

            if (sourceValue == null && mapFrom.ignoreNull()) {
                return;
            }

            // 检查是否需要脱敏
            Sensitive sensitive = targetField.getAnnotation(Sensitive.class);
            if (sensitive != null && sourceValue != null && sourceValue instanceof String) {
                sourceValue = SensitiveUtil.desensitize((String) sourceValue, sensitive);
            }

            // 设置目标字段值
            setFieldValue(target, targetField, sourceValue, mapFrom.converter());

        } catch (Exception e) {
            // 回调监听器
            if (listener != null) {
                listener.onFieldMappingError(target, targetField, source, e);
            }
        }
    }

    /**
     * 处理没有注解的字段（按字段名自动匹配）
     */
    private static void processFieldWithoutAnnotation(Object target, Field targetField, Object[] sources, MapperListener listener) {
        String fieldName = targetField.getName();

        for (Object source : sources) {
            try {
                Object sourceValue = getFieldValue(source, fieldName);
                if (sourceValue != null) {
                    setFieldValue(target, targetField, sourceValue, TypeConverter.DefaultConverter.class);
                    break; // 找到匹配后就不再查找其他源对象
                }
            } catch (Exception e) {
                // 回调监听器
                if (listener != null) {
                    listener.onFieldMappingError(target, targetField, source, e);
                }
            }
        }
    }

    /**
     * 获取嵌套字段值
     * 支持路径如：user.address.city
     */
    private static Object getNestedFieldValue(Object obj, String path) {
        if (obj == null || path == null || path.isEmpty()) {
            return null;
        }

        // 支持Map作为数据源
        if (obj instanceof Map) {
            return MapFieldValueProvider.getValue((Map<?, ?>) obj, path);
        }

        String[] parts = path.split("\\.");
        Object current = obj;

        for (String part : parts) {
            if (current == null) {
                return null;
            }
            // 检查是否为Map
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                current = getFieldValue(current, part);
            }
        }

        return current;
    }

    /**
     * 获取字段值（通过 getter 方法或直接访问，使用缓存）
     */
    static Object getFieldValue(Object obj, String fieldName) {
        try {
            Class<?> clazz = obj.getClass();
            // 使用缓存的 getter 方法
            Method getter = FieldInfoCache.getGetter(clazz, fieldName);
            if (getter != null) {
                return getter.invoke(obj);
            }

            // 使用缓存的字段
            Field field = FieldInfoCache.getField(clazz, fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 设置字段值（通过 setter 方法或直接访问，使用缓存）
     */
    private static void setFieldValue(Object target, Field field, Object value, Class<? extends TypeConverter> converterClass) {
        try {
            // 类型转换
            Object convertedValue;
            if (converterClass != null && converterClass != TypeConverter.class) {
                TypeConverter<Object, Object> converter = converterClass.getDeclaredConstructor().newInstance();
                convertedValue = converter.convert(value, (Class<Object>) field.getType());
            } else {
                convertedValue = TypeConverterUtil.convert(value, field.getType());
            }

            if (convertedValue == null && value != null) {
                // 转换失败，不设置值
                return;
            }

            // 使用缓存的 setter 方法
            Method setter = FieldInfoCache.getSetter(target.getClass(), field.getName(), field.getType());
            if (setter != null) {
                setter.invoke(target, convertedValue);
                return;
            }

            // 直接设置字段值
            field.setAccessible(true);
            field.set(target, convertedValue);

        } catch (Exception e) {
            // 忽略设置失败
        }
    }

    /**
     * 查找方法（包括父类）
     */
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 查找字段（包括父类）
     */
    private static Field findField(Class<?> clazz, String fieldName) {
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
     * 获取类的所有字段（包括父类）
     */
    private static Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * 将字符串首字母大写
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
