package com.github.leyland.data.desensitize;

import com.github.leyland.data.desensitize.handler.DesensitizeHandler;
import com.github.leyland.data.desensitize.handler.IndexDesensitizeHandler;
import com.github.leyland.data.desensitize.handler.RegexDesensitizeHandler;
import com.github.leyland.data.desensitize.handler.SlideDesensitizeHandler;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脱敏处理器持有者
 * 管理所有脱敏处理器实例
 *
 * @author leyland
 * @date 2025-01-12
 */
public final class DesensitizeHandlerHolder {

    private static final DesensitizeHandlerHolder INSTANCE = new DesensitizeHandlerHolder();

    private final Map<Class<? extends DesensitizeHandler>, DesensitizeHandler> handlerMap = new ConcurrentHashMap<>(16);

    private DesensitizeHandlerHolder() {
        // 注册默认处理器
        registerDefaultHandlers();
        // SPI 加载自定义处理器
        loadSpiHandlers();
    }

    /**
     * 注册默认处理器
     */
    private void registerDefaultHandlers() {
        // 注册默认的滑块脱敏处理器
        handlerMap.put(SlideDesensitizeHandler.class, new SlideDesensitizeHandler());
        // 注册默认的索引脱敏处理器
        handlerMap.put(IndexDesensitizeHandler.class, new IndexDesensitizeHandler());
        // 注册默认的正则脱敏处理器
        handlerMap.put(RegexDesensitizeHandler.class, new RegexDesensitizeHandler());
    }

    /**
     * 通过 SPI 加载自定义处理器
     */
    private void loadSpiHandlers() {
        ServiceLoader<DesensitizeHandler> loaders = ServiceLoader.load(DesensitizeHandler.class);
        for (DesensitizeHandler handler : loaders) {
            handlerMap.put(handler.getClass(), handler);
        }
    }

    /**
     * 获取处理器实例
     *
     * @param handlerClass 处理器类
     * @param <T> 处理器类型
     * @return 处理器实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends DesensitizeHandler> T getHandler(Class<T> handlerClass) {
        return (T) INSTANCE.handlerMap.get(handlerClass);
    }

    /**
     * 注册处理器
     *
     * @param handlerClass 处理器类
     * @param handler 处理器实例
     * @param <T> 处理器类型
     */
    public static <T extends DesensitizeHandler> void register(Class<T> handlerClass, T handler) {
        INSTANCE.handlerMap.put(handlerClass, handler);
    }

    /**
     * 获取滑块脱敏处理器
     *
     * @return 滑块脱敏处理器
     */
    public static SlideDesensitizeHandler getSlideHandler() {
        return getHandler(SlideDesensitizeHandler.class);
    }

    /**
     * 获取索引脱敏处理器
     *
     * @return 索引脱敏处理器
     */
    public static IndexDesensitizeHandler getIndexHandler() {
        return getHandler(IndexDesensitizeHandler.class);
    }

    /**
     * 获取正则脱敏处理器
     *
     * @return 正则脱敏处理器
     */
    public static RegexDesensitizeHandler getRegexHandler() {
        return getHandler(RegexDesensitizeHandler.class);
    }

    /**
     * 清空所有处理器
     */
    public static void clear() {
        INSTANCE.handlerMap.clear();
    }

    /**
     * 移除指定处理器
     *
     * @param handlerClass 处理器类
     */
    public static void unregister(Class<? extends DesensitizeHandler> handlerClass) {
        INSTANCE.handlerMap.remove(handlerClass);
    }
}
