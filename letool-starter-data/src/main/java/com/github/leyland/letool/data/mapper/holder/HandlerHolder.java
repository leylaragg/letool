package com.github.leyland.letool.data.mapper.holder;

import com.github.leyland.letool.data.mapper.handler.FieldMappingHandler;
import com.github.leyland.letool.data.mapper.handler.FormatMappingHandler;
import com.github.leyland.letool.data.mapper.handler.SensitiveMappingHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字段映射处理器持有者
 * 管理所有字段映射处理器实例
 *
 * @author leyland
 * @date 2025-01-12
 */
public final class HandlerHolder {

    /**
     * 处理器列表（按优先级排序）
     */
    private final List<FieldMappingHandler> handlers = new ArrayList<>();

    /**
     * 处理器缓存
     */
    private final ConcurrentHashMap<String, FieldMappingHandler> handlerCache = new ConcurrentHashMap<>();

    /**
     * 初始化标志
     */
    private volatile boolean initialized = false;

    private HandlerHolder() {
    }

    /**
     * 获取单例实例
     */
    public static HandlerHolder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * 内部静态类，用于延迟初始化
     */
    private static class InstanceHolder {
        private static final HandlerHolder INSTANCE = new HandlerHolder();
    }

    /**
     * 初始化默认处理器（延迟加载）
     */
    private void initializeIfNecessary() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    registerDefaultHandlers();
                    loadSpiHandlers();
                    sortHandlers();
                    initialized = true;
                }
            }
        }
    }

    /**
     * 注册默认处理器（直接添加，避免递归）
     */
    private void registerDefaultHandlers() {
        try {
            // 直接添加脱敏处理器（不调用 register 方法，避免递归）
            SensitiveMappingHandler sensitiveHandler = new SensitiveMappingHandler();
            String key = sensitiveHandler.getClass().getName();
            handlerCache.put(key, sensitiveHandler);
            handlers.add(sensitiveHandler);
        } catch (Exception e) {
            // 忽略注册失败，可能是类路径问题
        }
        try {
            // 直接添加格式化处理器（不调用 register 方法，避免递归）
            FormatMappingHandler formatHandler = new FormatMappingHandler();
            String key = formatHandler.getClass().getName();
            handlerCache.put(key, formatHandler);
            handlers.add(formatHandler);
        } catch (Exception e) {
            // 忽略注册失败，可能是类路径问题
        }
    }

    /**
     * 通过 SPI 加载自定义处理器（直接添加，避免递归）
     */
    private void loadSpiHandlers() {
        try {
            ServiceLoader<FieldMappingHandler> loaders = ServiceLoader.load(FieldMappingHandler.class);
            for (FieldMappingHandler handler : loaders) {
                // 直接添加（不调用 register 方法，避免递归）
                String key = handler.getClass().getName();
                handlerCache.put(key, handler);
                handlers.add(handler);
            }
        } catch (Exception e) {
            // 忽略 SPI 加载失败
        }
    }

    /**
     * 对处理器按优先级排序
     */
    private void sortHandlers() {
        handlers.sort(Comparator.comparingInt(FieldMappingHandler::getPriority));
    }

    /**
     * 获取处理器实例
     *
     * @param handlerClass 处理器类
     * @param <T> 处理器类型
     * @return 处理器实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends FieldMappingHandler> T getHandler(Class<T> handlerClass) {
        if (handlerClass == null) {
            return null;
        }

        getInstance().initializeIfNecessary();

        String key = handlerClass.getName();
        FieldMappingHandler handler = getInstance().handlerCache.get(key);
        if (handler == null) {
            // 尝试创建新实例
            try {
                handler = handlerClass.getDeclaredConstructor().newInstance();
                getInstance().handlerCache.put(key, handler);
                getInstance().handlers.add(handler);
                getInstance().sortHandlers();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create handler instance: " + handlerClass.getName(), e);
            }
        }
        return (T) handler;
    }

    /**
     * 获取所有处理器
     *
     * @return 处理器列表
     */
    public static List<FieldMappingHandler> getAllHandlers() {
        getInstance().initializeIfNecessary();
        return new ArrayList<>(getInstance().handlers);
    }

    /**
     * 注册处理器
     *
     * @param handler 处理器实例
     */
    public static void register(FieldMappingHandler handler) {
        if (handler == null) {
            return;
        }

        getInstance().initializeIfNecessary();

        String key = handler.getClass().getName();
        getInstance().handlerCache.put(key, handler);
        getInstance().handlers.add(handler);
        getInstance().sortHandlers();
    }

    /**
     * 移除指定处理器
     *
     * @param handlerClass 处理器类
     */
    public static void unregister(Class<? extends FieldMappingHandler> handlerClass) {
        if (handlerClass == null) {
            return;
        }

        String key = handlerClass.getName();
        getInstance().handlerCache.remove(key);
        getInstance().handlers.removeIf(h -> h.getClass().getName().equals(key));
    }

    /**
     * 清空所有处理器
     */
    public static void clear() {
        getInstance().handlers.clear();
        getInstance().handlerCache.clear();
        getInstance().initialized = false;
    }
}
