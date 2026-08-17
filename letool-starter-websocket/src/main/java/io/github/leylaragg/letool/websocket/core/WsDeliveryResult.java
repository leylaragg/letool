package io.github.leylaragg.letool.websocket.core;

/**
 * 一次进程内 WebSocket 推送的不可变结果。
 */
public final class WsDeliveryResult {

    private final int targetCount;
    private final int successCount;
    private final int failureCount;
    private final int staleSessionCount;

    /**
     * 创建投递结果。
     *
     * @param targetCount 目标连接数
     * @param successCount 成功连接数
     * @param failureCount 发送失败连接数
     * @param staleSessionCount 已失效连接数
     */
    public WsDeliveryResult(
            int targetCount,
            int successCount,
            int failureCount,
            int staleSessionCount) {
        if (targetCount < 0 || successCount < 0 || failureCount < 0 || staleSessionCount < 0
                || successCount + failureCount + staleSessionCount != targetCount) {
            throw new IllegalArgumentException("delivery counts are inconsistent");
        }
        this.targetCount = targetCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.staleSessionCount = staleSessionCount;
    }

    /**
     * 创建空投递结果。
     *
     * @return 空投递结果
     */
    public static WsDeliveryResult empty() {
        return new WsDeliveryResult(0, 0, 0, 0);
    }

    /** @return 目标连接数 */
    public int getTargetCount() {
        return targetCount;
    }

    /** @return 成功连接数 */
    public int getSuccessCount() {
        return successCount;
    }

    /** @return 发送失败连接数 */
    public int getFailureCount() {
        return failureCount;
    }

    /** @return 已失效连接数 */
    public int getStaleSessionCount() {
        return staleSessionCount;
    }

    /**
     * 判断全部目标是否成功接收。
     *
     * @return 全部成功时返回 {@code true}
     */
    public boolean isAllSuccessful() {
        return failureCount == 0 && staleSessionCount == 0;
    }
}
