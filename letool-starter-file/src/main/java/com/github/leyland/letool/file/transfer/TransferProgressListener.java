package com.github.leyland.letool.file.transfer;

/**
 * 传输进度变更监听扩展点。
 */
@FunctionalInterface
public interface TransferProgressListener {

    /**
     * 接收不可变进度快照。
     *
     * @param progress 最新进度
     */
    void onProgress(TransferProgress progress);
}
