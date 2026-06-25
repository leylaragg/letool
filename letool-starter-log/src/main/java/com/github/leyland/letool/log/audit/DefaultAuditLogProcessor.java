package com.github.leyland.letool.log.audit;

import com.github.leyland.letool.log.store.LogRecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 默认审计日志处理器 —— 异步写入存储，不阻塞主业务流程.
 *
 * <h3>架构决策</h3>
 * <ul>
 *   <li><b>为什么异步？</b> 审计日志不应影响业务响应时间。文件 IO / 数据库写入在独立线程中完成</li>
 *   <li><b>为什么单线程？</b> 保证日志写入顺序（先发生的先写入），避免并发写入导致文件行序错乱</li>
 *   <li><b>为什么 Daemon 线程？</b> JVM 关闭时允许直接退出，不等待未完成的日志写入</li>
 *   <li><b>写入失败静默处理</b> 仅记录错误日志，不抛异常影响主流程</li>
 * </ul>
 */
public class DefaultAuditLogProcessor implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuditLogProcessor.class);

    private final LogRecordStore<AuditLogEvent> store;
    private final ExecutorService executor;

    public DefaultAuditLogProcessor(LogRecordStore<AuditLogEvent> store) {
        this.store = store;

        // 单线程执行器：保证日志写入的顺序性
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audit-log-writer");
            // Daemon 线程：不阻止 JVM 退出
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void record(AuditLogEvent event) {
        // ==== 异步提交写入任务，主线程立即返回 ====
        executor.submit(() -> {
            try {
                store.save(event);
            } catch (Exception e) {
                // ==== 写入失败仅记录错误日志，不影响业务 ====
                log.error("审计日志写入失败: {}", e.getMessage());
            }
        });
    }
}
