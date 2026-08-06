package com.github.leyland.letool.tool.http;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * HTTP 请求构建、传输和响应读取失败时抛出的统一基础设施异常。
 *
 * <p>异常保留底层原因链用于受控诊断，但默认消息不会拼接 URL、查询参数、请求体、响应体或请求头，
 * 避免认证信息和业务数据进入对外响应或普通日志。</p>
 */
public final class HttpException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建统一 HTTP 异常。
     *
     * @param errorCode 稳定 HTTP 错误码
     * @param cause 底层失败原因
     */
    private HttpException(HttpErrorCode errorCode, Throwable cause) {
        super(errorCode, null, null, requireCause(cause));
    }

    /**
     * 创建请求参数无效异常。
     *
     * @param cause 参数解析或校验失败原因
     * @return 请求参数异常
     */
    public static HttpException invalidRequest(Throwable cause) {
        return new HttpException(HttpErrorCode.INVALID_REQUEST, cause);
    }

    /**
     * 创建请求传输失败异常。
     *
     * @param cause 底层连接或读写失败原因
     * @return 请求传输异常
     */
    public static HttpException requestFailed(Throwable cause) {
        return new HttpException(HttpErrorCode.REQUEST_FAILED, cause);
    }

    /**
     * 创建请求超时异常。
     *
     * @param cause 底层超时原因
     * @return 请求超时异常
     */
    public static HttpException requestTimeout(Throwable cause) {
        return new HttpException(HttpErrorCode.REQUEST_TIMEOUT, cause);
    }

    /**
     * 创建请求被中断异常。
     *
     * @param cause 线程中断原因
     * @return 请求中断异常
     */
    public static HttpException requestInterrupted(Throwable cause) {
        return new HttpException(HttpErrorCode.REQUEST_INTERRUPTED, cause);
    }

    /**
     * 创建响应体超过内存读取上限异常。
     *
     * @param cause 响应订阅被取消的底层原因
     * @return 响应体越界异常
     */
    public static HttpException responseTooLarge(Throwable cause) {
        return new HttpException(HttpErrorCode.RESPONSE_TOO_LARGE, cause);
    }

    /**
     * 创建用户拦截器执行失败异常。
     *
     * @param cause 拦截器抛出的原始异常
     * @return 拦截器异常
     */
    public static HttpException interceptorFailed(Throwable cause) {
        return new HttpException(HttpErrorCode.INTERCEPTOR_FAILED, cause);
    }

    /**
     * 校验需要保留的底层异常原因。
     *
     * @param cause 底层异常原因
     * @return 校验通过的异常原因
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }
}
