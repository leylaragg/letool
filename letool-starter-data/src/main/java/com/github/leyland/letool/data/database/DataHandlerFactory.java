package com.github.leyland.letool.data.database;

import com.github.leyland.letool.data.database.handler.DataHandler;
import com.github.leyland.letool.data.database.handler.DefaultDataHandler;
import com.github.leyland.letool.tool.configuration.SpringUtil;
//import com.github.leyland.letool.data.database.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @ClassName <h2>DataHandlerFactory</h2>
 * @Description     数据处理器工厂
 *                      根据不同的查询键返回相应的数据处理器
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
@Component
@ConditionalOnProperty(prefix = "letool.database", name = "enabled", havingValue = "true")
public class DataHandlerFactory implements SmartInitializingSingleton {

    private final DefaultDataHandler defaultDataHandler;

    private final Map<String, DataHandler> handlerMap = new ConcurrentHashMap<>();

    public DataHandlerFactory(DefaultDataHandler defaultDataHandler) {
        this.defaultDataHandler = defaultDataHandler;
//        // 初始化默认处理器
//        Map<String, DataHandler> beansOfType = SpringUtil.getBeansOfType(DataHandler.class);
//        beansOfType.forEach(this::registerHandler);
    }

    /**
     * Invoked right at the end of the singleton pre-instantiation phase,
     * with a guarantee that all regular singleton beans have been created
     * already. {@link ListableBeanFactory#getBeansOfType} calls within
     * this method won't trigger accidental side effects during bootstrap.
     * <p><b>NOTE:</b> This callback won't be triggered for singleton beans
     * lazily initialized on demand after {@link BeanFactory} bootstrap,
     * and not for any other bean scope either. Carefully use it for beans
     * with the intended bootstrap semantics only.
     */
    @Override
    public void afterSingletonsInstantiated() {
        // 初始化默认处理器
        Map<String, DataHandler> beansOfType = SpringUtil.getBeansOfType(DataHandler.class);
        beansOfType.forEach(this::registerHandler);
    }

    /**
     * 注册数据处理器
     *
     * @param queryKey 查询键
     * @param handler 数据处理器
     */
    public void registerHandler(String queryKey, DataHandler handler) {
        handlerMap.put(queryKey, handler);
        log.info("注册数据处理器: {} -> {}", queryKey, handler.getClass().getSimpleName());
    }

    /**
     * 获取数据处理器
     *
     * @param queryKey 查询键
     * @return 数据处理器，如果没有注册则返回默认处理器
     */
    public DataHandler getHandler(String queryKey) {
        DataHandler handler = handlerMap.get(queryKey);
        if (handler == null) {
            log.debug("未找到查询键 {} 的处理器，使用默认处理器", queryKey);
            return defaultDataHandler;
        }
        return handler;
    }

    /**
     * 检查是否有自定义处理器
     *
     * @param queryKey 查询键
     * @return 是否存在自定义处理器
     */
    public boolean hasCustomHandler(String queryKey) {
        return handlerMap.containsKey(queryKey);
    }

    /**
     * 获取所有已注册的查询键
     *
     * @return 查询键集合
     */
    public java.util.Set<String> getRegisteredQueryKeys() {
        return handlerMap.keySet();
    }
}
