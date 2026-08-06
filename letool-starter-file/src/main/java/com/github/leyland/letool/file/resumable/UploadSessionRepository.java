package com.github.leyland.letool.file.resumable;

import com.github.leyland.letool.file.resumable.model.UploadSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 断点续传会话持久化扩展接口。
 */
public interface UploadSessionRepository {

    /**
     * 创建不存在的会话。
     *
     * @param session 初始会话
     * @return 已持久化会话
     */
    UploadSession create(UploadSession session);

    /**
     * 查询会话。
     *
     * @param uploadId 上传会话编号
     * @return 会话；不存在时为空
     */
    Optional<UploadSession> find(String uploadId);

    /**
     * 以乐观版本保存会话。
     *
     * @param session 新会话快照
     * @param expectedVersion 预期当前版本
     * @return 版本已递增的持久化会话
     */
    UploadSession save(UploadSession session, long expectedVersion);

    /**
     * 查询指定时间已经过期的会话。
     *
     * @param now 当前时间
     * @return 过期会话列表
     */
    List<UploadSession> findExpired(Instant now);

    /**
     * 以乐观版本删除会话。
     *
     * @param uploadId 上传会话编号
     * @param expectedVersion 预期当前版本
     * @return 是否删除成功
     */
    boolean delete(String uploadId, long expectedVersion);
}
