package com.github.leyland.letool.data.mapper.holder;

import com.github.leyland.letool.data.mapper.converter.DateFieldConverter;
import com.github.leyland.letool.data.mapper.converter.FieldConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段转换器持有者
 * 管理所有字段转换器实例
 *
 * @author leyland
 * @date 2025-01-12
 */
public final class ConverterHolder {

    /**
     * 转换器列表
     */
    private final List<FieldConverter<?, ?>> converters = new ArrayList<>();

    /**
     * 转换器缓存（Key: 源类型#目标类型）
     */
    private final ConcurrentHashMap<String, FieldConverter<?, ?>> converterCache = new ConcurrentHashMap<>();

    /**
     * 初始化标志
     */
    private volatile boolean initialized = false;

    private ConverterHolder() {
    }

    /**
     * 获取单例实例
     */
    public static ConverterHolder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * 内部静态类，用于延迟初始化
     */
    private static class InstanceHolder {
        private static final ConverterHolder INSTANCE = new ConverterHolder();
    }

    /**
     * 初始化默认转换器（延迟加载）
     */
    private void initializeIfNecessary() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    registerDefaultConverters();
                    loadSpiConverters();
                    sortConverters();
                    initialized = true;
                }
            }
        }
    }

    /**
     * 注册默认转换器（直接添加，避免递归）
     */
    private void registerDefaultConverters() {
        try {
            // 直接添加日期转换器（不调用 register 方法，避免递归）
            DateFieldConverter dateConverter = new DateFieldConverter();
            String key = dateConverter.getClass().getName();
            converterCache.put(key, dateConverter);
            converters.add(dateConverter);
        } catch (Exception e) {
            // 忽略注册失败，可能是类路径问题
        }
    }

    /**
     * 通过 SPI 加载自定义转换器（直接添加，避免递归）
     */
    private void loadSpiConverters() {
        try {
            ServiceLoader<FieldConverter> loaders = ServiceLoader.load(FieldConverter.class);
            for (FieldConverter<?, ?> converter : loaders) {
                // 直接添加（不调用 register 方法，避免递归）
                String key = converter.getClass().getName();
                converterCache.put(key, converter);
                converters.add(converter);
            }
        } catch (Exception e) {
            // 忽略 SPI 加载失败
        }
    }

    /**
     * 对转换器按优先级排序
     */
    private void sortConverters() {
        converters.sort(Comparator.comparingInt(FieldConverter::getPriority));
    }

    /**
     * 查找转换器
     *
     * @param sourceType 源类型
     * @param targetType 目标类型
     * @return 转换器
     */
    @SuppressWarnings("unchecked")
    public static <S, T> FieldConverter<S, T> find(Class<S> sourceType, Class<T> targetType) {
        if (sourceType == null || targetType == null) {
            return null;
        }

        getInstance().initializeIfNecessary();

        String key = sourceType.getName() + "#" + targetType.getName();
        FieldConverter<?, ?> converter = getInstance().converterCache.get(key);
        if (converter != null) {
            return (FieldConverter<S, T>) converter;
        }

        // 查找支持该转换的转换器
        for (FieldConverter<?, ?> c : getInstance().converters) {
            if (c.supports(sourceType, targetType)) {
                getInstance().converterCache.put(key, c);
                return (FieldConverter<S, T>) c;
            }
        }

        return null;
    }

    /**
     * 获取转换器实例
     *
     * @param converterClass 转换器类
     * @param <T> 转换器类型
     * @return 转换器实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends FieldConverter<?, ?>> T getConverter(Class<T> converterClass) {
        if (converterClass == null) {
            return null;
        }

        getInstance().initializeIfNecessary();

        String key = converterClass.getName();
        FieldConverter<?, ?> converter = getInstance().converterCache.get(key);
        if (converter == null) {
            try {
                converter = converterClass.getDeclaredConstructor().newInstance();
                getInstance().converterCache.put(key, converter);
                getInstance().converters.add(converter);
                getInstance().sortConverters();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create converter instance: " + converterClass.getName(), e);
            }
        }
        return (T) converter;
    }

    /**
     * 获取所有转换器
     *
     * @return 转换器列表
     */
    public static List<FieldConverter<?, ?>> getAllConverters() {
        getInstance().initializeIfNecessary();
        return new ArrayList<>(getInstance().converters);
    }

    /**
     * 注册转换器
     *
     * @param converter 转换器实例
     */
    public static void register(FieldConverter<?, ?> converter) {
        if (converter == null) {
            return;
        }

        getInstance().initializeIfNecessary();

        String key = converter.getClass().getName();
        getInstance().converterCache.put(key, converter);
        getInstance().converters.add(converter);
        getInstance().sortConverters();
    }

    /**
     * 移除转换器
     *
     * @param converterClass 转换器类
     */
    public static void unregister(Class<? extends FieldConverter<?, ?>> converterClass) {
        if (converterClass == null) {
            return;
        }

        String key = converterClass.getName();
        getInstance().converterCache.remove(key);
        getInstance().converters.removeIf(c -> c.getClass().getName().equals(key));
    }

    /**
     * 清空所有转换器
     */
    public static void clear() {
        getInstance().converters.clear();
        getInstance().converterCache.clear();
        getInstance().initialized = false;
    }
}
