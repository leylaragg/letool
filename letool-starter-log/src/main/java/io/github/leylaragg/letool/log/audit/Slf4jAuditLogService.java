package io.github.leylaragg.letool.log.audit;

import io.github.leylaragg.letool.tool.json.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 通过专用 SLF4J Logger 输出结构化审计事件的默认实现。
 *
 * <p>该实现不自行维护文件、线程池或数据库连接。异步输出、日志滚动、集中采集和保留周期
 * 由应用选用的 Logback、Log4j2 或其他 SLF4J 后端负责。需要数据库、消息队列或搜索引擎
 * 持久化时，业务应用可以直接替换 {@link AuditLogService} Bean。</p>
 */
public class Slf4jAuditLogService implements AuditLogService {

    /** 用户配置日志后端时使用的专用审计 Logger 名称。 */
    public static final String AUDIT_LOGGER_NAME = "letool.audit";

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger(AUDIT_LOGGER_NAME);

    private final JsonCodec jsonCodec;

    /**
     * 创建结构化审计日志服务。
     *
     * @param jsonCodec 用于序列化审计事件的 JSON 编解码器
     * @throws NullPointerException 当 {@code jsonCodec} 为 {@code null} 时抛出
     */
    public Slf4jAuditLogService(JsonCodec jsonCodec) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
    }

    /**
     * 将审计事件序列化为单行 JSON，并写入专用 Logger。
     *
     * @param event 待记录的审计事件，不允许为 {@code null}
     * @throws NullPointerException 当 {@code event} 为 {@code null} 时抛出
     */
    @Override
    public void record(AuditLogEvent event) {
        AuditLogEvent requiredEvent = Objects.requireNonNull(event, "event must not be null");
        if (AUDIT_LOGGER.isInfoEnabled()) {
            AUDIT_LOGGER.info("{}", jsonCodec.write(requiredEvent));
        }
    }
}
