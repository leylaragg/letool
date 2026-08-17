package io.github.leylaragg.letool.monitor.cleanup;

import java.time.Duration;

/**
 * 用户实现的数据清理任务。
 *
 * <p>Letool 只负责调度和执行报告，不提供默认 SQL 或空实现。应用可以使用
 * JdbcTemplate、MyBatis-Plus、Spring Data 或远程存储客户端完成真实清理。</p>
 */
public interface CleanupTask {

    /**
     * 获取稳定且唯一的任务名称。
     *
     * @return 非空任务名称
     */
    String name();

    /**
     * 获取数据保留时长。
     *
     * @return 正数保留时长
     */
    Duration retention();

    /**
     * 执行真实数据清理。
     *
     * @param context 包含统一触发时间和清理截止时间的上下文
     * @return 非负受影响记录数
     */
    long cleanup(CleanupContext context);
}
