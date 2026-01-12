package com.github.leyland.data.mapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段信息缓存
 * 缓存反射相关的字段和方法信息，提升性能
 *
 * @author leyland
 * @date 2025-01-08
 */
public class FieldInfoCache {

    /**
     * 类的所有字段缓存
     * Key: 类名, Value: 字段数组
     */
    private static final Map<String, Field[]> CLASS_FIELDS_CACHE = new ConcurrentHashMap<>();

    /**
     * Getter 方法缓存
     * Key: 类名#字段名, Value: Method
     */
    private static final Map<String, Method> GETTER_CACHE = new ConcurrentHashMap<>();

    /**
     * Setter 方法缓存
     * Key: 类名#字段名, Value: Method
     */
    private static final Map<String, Method> SETTER_CACHE = new ConcurrentHashMap<>();

    /**
     * 字段缓存
     * Key: 类名#字段名, Value: Field
     */
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取类的所有字段（带缓存）
     */
    public static Field[] getAllFields(Class<?> clazz) {
        String className = clazz.getName();
        return CLASS_FIELDS_CACHE.computeIfAbsent(className, k -> {
            java.util.List<Field> fields = new java.util.ArrayList<>();
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                fields.addAll(java.util.Arrays.asList(current.getDeclaredFields()));
                current = current.getSuperclass();
            }
            return fields.toArray(new Field[0]);
        });
    }

    /**
     * 获取 Getter 方法（带缓存）
     */
    public static Method getGetter(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "#" + fieldName;
        return GETTER_CACHE.computeIfAbsent(key, k -> findGetter(clazz, fieldName));
    }

    /**
     * 获取 Setter 方法（带缓存）
     */
    public static Method getSetter(Class<?> clazz, String fieldName, Class<?> paramType) {
        String key = clazz.getName() + "#" + fieldName + "#" + paramType.getName();
        return SETTER_CACHE.computeIfAbsent(key, k -> findSetter(clazz, fieldName, paramType));
    }

    /**
     * 获取字段（带缓存）
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "#" + fieldName;
        return FIELD_CACHE.computeIfAbsent(key, k -> findField(clazz, fieldName));
    }

    /**
     * 清空所有缓存
     */
    public static void clearCache() {
        CLASS_FIELDS_CACHE.clear();
        GETTER_CACHE.clear();
        SETTER_CACHE.clear();
        FIELD_CACHE.clear();
    }

    /**
     * 清空指定类的缓存
     */
    public static void clearClassCache(Class<?> clazz) {
        String className = clazz.getName();
        CLASS_FIELDS_CACHE.remove(className);

        // 清除相关的 getter/setter/field 缓存
        GETTER_CACHE.keySet().removeIf(key -> key.startsWith(className + "#"));
        SETTER_CACHE.keySet().removeIf(key -> key.startsWith(className + "#"));
        FIELD_CACHE.keySet().removeIf(key -> key.startsWith(className + "#"));
    }

    // ==================== 私有方法 ====================

    private static Method findGetter(Class<?> clazz, String fieldName) {
        String getterName = "get" + capitalize(fieldName);
        Method getter = findMethod(clazz, getterName);
        if (getter != null) {
            return getter;
        }

        String isMethodName = "is" + capitalize(fieldName);
        return findMethod(clazz, isMethodName);
    }

    private static Method findSetter(Class<?> clazz, String fieldName, Class<?> paramType) {
        String setterName = "set" + capitalize(fieldName);
        return findMethod(clazz, setterName, paramType);
    }

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

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
