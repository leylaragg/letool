package io.github.leylaragg.letool.file.transfer;

import java.util.Optional;

/**
 * 按传输编号维护可信进度的扩展接口。
 */
public interface TransferProgressMonitor {

    /**
     * 生成适合公开传递的随机传输编号。
     *
     * @return 新传输编号
     */
    String generateTransferId();

    /**
     * 创建并开始传输记录。
     *
     * @param transferId 传输编号
     * @param type 传输类型
     * @param totalBytes 总字节数；未知时传 {@code -1}
     * @param initialBytes 初始已确认字节数
     * @return 初始进度
     */
    TransferProgress begin(
            String transferId,
            TransferType type,
            long totalBytes,
            long initialBytes);

    /**
     * 单调更新已确认传输字节数。
     *
     * @param transferId 传输编号
     * @param transferredBytes 已确认字节数
     * @return 更新后的进度
     */
    TransferProgress update(String transferId, long transferredBytes);

    /**
     * 转换传输状态。
     *
     * @param transferId 传输编号
     * @param status 目标状态
     * @param safeFailureReason 对业务安全的失败原因
     * @return 更新后的进度
     */
    TransferProgress transition(
            String transferId,
            TransferStatus status,
            String safeFailureReason);

    /**
     * 查询传输进度。
     *
     * @param transferId 传输编号
     * @return 进度；不存在或已清理时为空
     */
    Optional<TransferProgress> find(String transferId);

    /**
     * 主动移除进度记录。
     *
     * @param transferId 传输编号
     */
    void remove(String transferId);
}
