package com.github.leyland.letool.web.exception;

import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.web.code.WebErrorCode;

import java.io.Serial;

/**
 * 表示请求体缓存读取超过 Web 模块允许的字节上限。
 *
 * <p>异常保存实际读取大小和配置上限供服务端诊断，但面向客户端的消息始终来自
 * {@link WebErrorCode#REQUEST_BODY_TOO_LARGE}，不会暴露内部缓存策略。</p>
 */
public final class RequestBodyTooLargeException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 检测到的请求体大小。 */
    private final long actualSize;

    /** 允许缓存的最大请求体大小。 */
    private final long maxSize;

    /**
     * 创建请求体过大异常。
     *
     * @param actualSize 已声明或实际读取到的请求体字节数
     * @param maxSize 允许缓存的最大字节数
     * @throws IllegalArgumentException 当大小参数不合法时抛出
     */
    public RequestBodyTooLargeException(long actualSize, long maxSize) {
        super(WebErrorCode.REQUEST_BODY_TOO_LARGE, null, null, null);
        if (actualSize < 0) {
            throw new IllegalArgumentException("actualSize must not be negative");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.actualSize = actualSize;
        this.maxSize = maxSize;
    }

    /**
     * 获取检测到的请求体字节数。
     *
     * @return 实际或已声明字节数
     */
    public long getActualSize() {
        return actualSize;
    }

    /**
     * 获取允许缓存的最大字节数。
     *
     * @return 最大字节数
     */
    public long getMaxSize() {
        return maxSize;
    }
}
