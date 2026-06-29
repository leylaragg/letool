package com.github.leyland.letool.ratelimiter.circuit;

/**
 * 熔断器核心接口 —— 定义熔断保护的标准契约。
 *
 * <p>熔断器是一种用于保护分布式系统稳定性的设计模式。当下游服务故障率
 * 超过阈值时，熔断器自动"断开电路"，对后续请求直接返回失败（快速失败），
 * 避免级联故障（雪崩效应）。</p>
 *
 * <h3>三种状态</h3>
 * <ul>
 *   <li><b>{@link CircuitBreakerState#CLOSED}</b> —— 正常工作，请求正常通过，统计失败率</li>
 *   <li><b>{@link CircuitBreakerState#OPEN}</b> —— 熔断打开，拒绝所有请求，快速失败</li>
 *   <li><b>{@link CircuitBreakerState#HALF_OPEN}</b> —— 半开状态，允许少量试探请求</li>
 * </ul>
 *
 * <h3>典型使用流程</h3>
 * <pre>{@code
 * @Autowired
 * private CircuitBreaker circuitBreaker;
 *
 * public Result callExternalService(Request req) {
 *     if (!circuitBreaker.isAllowed()) {
 *         return Result.error("服务暂时不可用");  // 快速失败
 *     }
 *     try {
 *         Result result = externalService.call(req);
 *         circuitBreaker.recordSuccess();        // 记录成功
 *         return result;
 *     } catch (Exception e) {
 *         circuitBreaker.recordFailure();        // 记录失败
 *         throw e;
 *     }
 * }
 * }</pre>
 *
 * <p>更推荐使用 {@link com.github.leyland.letool.ratelimiter.annotation.CircuitBreak @CircuitBreak}
 * 注解进行声明式熔断，框架会自动处理上述样板代码。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see DefaultCircuitBreaker
 * @see CircuitBreakerState
 */
public interface CircuitBreaker {

    // ======================== 请求通行检查 ========================

    /**
     * 判断当前请求是否允许通过。
     *
     * <p>根据熔断器当前状态决定：</p>
     * <ul>
     *   <li><b>CLOSED</b>：允许通过</li>
     *   <li><b>OPEN</b>：检查是否已到恢复超时，若是则切换到 HALF_OPEN 并允许；
     *       否则拒绝</li>
     *   <li><b>HALF_OPEN</b>：检查当前试探请求数是否已满，未满则允许；已满则拒绝</li>
     * </ul>
     *
     * @return {@code true} 允许请求通过，{@code false} 拒绝（熔断中）
     */
    boolean isAllowed();

    // ======================== 结果记录 ========================

    /**
     * 记录一次成功的请求。
     *
     * <p>在 HALF_OPEN 状态下，成功次数达到阈值后将恢复到 CLOSED 状态。
     * 在 CLOSED 状态下，记录用于计算失败率。</p>
     */
    void recordSuccess();

    /**
     * 记录一次失败的请求。
     *
     * <p>在 CLOSED 状态下，如果窗口内失败率超过阈值，将切换到 OPEN 状态。
     * 在 HALF_OPEN 状态下，任何一次失败都将立即切换回 OPEN 状态。</p>
     */
    void recordFailure();

    // ======================== 状态查询 ========================

    /**
     * 获取熔断器的当前状态。
     *
     * @return 熔断器状态（CLOSED / OPEN / HALF_OPEN）
     */
    CircuitBreakerState getState();

    // ======================== 状态管理 ========================

    /**
     * 强制重置熔断器到 CLOSED 状态，清除所有统计数据。
     *
     * <p>适用于以下场景：</p>
     * <ul>
     *   <li>手动恢复：确认下游服务已恢复后手动重置</li>
     *   <li>测试环境：测试前清除历史数据</li>
     * </ul>
     */
    void reset();
}
