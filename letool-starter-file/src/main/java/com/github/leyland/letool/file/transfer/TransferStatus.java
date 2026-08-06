package com.github.leyland.letool.file.transfer;

/**
 * 文件传输生命周期状态。
 */
public enum TransferStatus {

    /** 已创建但尚未开始传输。 */
    CREATED,

    /** 正在传输。 */
    RUNNING,

    /** 已暂停，等待后续恢复。 */
    PAUSED,

    /** 已完成数据传输，正在执行最终提交。 */
    FINALIZING,

    /** 已成功完成。 */
    COMPLETED,

    /** 传输失败。 */
    FAILED,

    /** 已由调用方取消。 */
    CANCELLED,

    /** 已过期。 */
    EXPIRED;

    /**
     * 判断当前状态是否为不可继续转换的终态。
     *
     * @return 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
