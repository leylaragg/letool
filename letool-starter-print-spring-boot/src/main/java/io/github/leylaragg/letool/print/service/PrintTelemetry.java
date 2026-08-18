package io.github.leylaragg.letool.print.service;

/**
 * 接收不包含业务数据的打印执行快照。
 *
 * <p>实现可以写入指标或审计系统，但自身故障不会改变打印主链路。</p>
 *
 * @author leyland
 */
@FunctionalInterface
public interface PrintTelemetry {

    /** 未配置观测实现时使用的无操作实例。 */
    PrintTelemetry NO_OP = snapshot -> { };

    /**
     * 记录一次完整同步打印的安全快照。
     *
     * @param snapshot 不含请求、模板正文和异常消息的执行结果
     */
    void record(PrintExecutionSnapshot snapshot);
}
