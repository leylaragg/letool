package com.github.leyland.letool.rule.operation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运算执行器工厂
 * 根据运算符类型获取对应的执行器
 *
 * @author leyland
 * @since 2026/02/14
 */
@Slf4j
@Component
public class OperationExecutorFactory {

    @Autowired
    private List<OperationExecutor> executors;

    private final Map<String, OperationExecutor> executorCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("初始化运算执行器工厂，已注册 {} 个执行器", executors.size());
    }

    /**
     * 获取运算执行器
     *
     * @param operator  运算符编码
     * @param leftType  左操作数类型
     * @param rightType 右操作数类型
     * @return 运算执行器
     */
    public OperationExecutor getExecutor(String operator, String leftType, String rightType) {
        // 构建缓存键
        String cacheKey = buildCacheKey(operator, leftType, rightType);

        // 从缓存获取
        return executorCache.computeIfAbsent(cacheKey, k -> {
            // 遍历查找支持的执行器
            for (OperationExecutor executor : executors) {
                if (executor.supports(operator, leftType, rightType)) {
                    log.debug("为运算符 {} 选择执行器: {}", operator, executor.getClass().getSimpleName());
                    return executor;
                }
            }
            // 默认返回核心执行器
            log.warn("未找到运算符 {} 的专用执行器，使用核心执行器", operator);
            return executors.stream()
                    .filter(e -> e instanceof CoreOperationExecutor)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("未找到核心运算执行器"));
        });
    }

    /**
     * 执行运算（便捷方法）
     */
    public OperationResult execute(OperationRequest request) {
        OperationExecutor executor = getExecutor(
                request.getOperator(),
                request.getLeftType(),
                request.getRightType()
        );
        return executor.execute(request);
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String operator, String leftType, String rightType) {
        return String.format("%s_%s_%s",
                operator,
                leftType != null ? leftType : "UNKNOWN",
                rightType != null ? rightType : "UNKNOWN");
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        executorCache.clear();
        log.info("运算执行器缓存已清除");
    }
}
