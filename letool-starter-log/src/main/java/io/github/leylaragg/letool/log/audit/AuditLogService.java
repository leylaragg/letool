package io.github.leylaragg.letool.log.audit;

/**
 * 消费结构化审计事件的持久化扩展接口。
 *
 * <p>Starter 默认将事件写入专用 SLF4J Logger。业务应用需要写入数据库、消息队列、
 * 搜索引擎或其他介质时，可以声明自己的实现覆盖默认 Bean。</p>
 */
public interface AuditLogService {

    /**
     * 消费一条审计事件。
     *
     * @param event 已完成上下文补充的审计事件
     */
    void record(AuditLogEvent event);
}
