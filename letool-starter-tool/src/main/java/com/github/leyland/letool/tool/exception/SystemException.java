package com.github.leyland.letool.tool.exception;

/**
 * 系统异常——因内部服务故障、资源不可用、外部依赖异常等原因导致操作无法完成时抛出.
 *
 * <p>此异常通常映射为 HTTP 5xx 响应码，表示服务端自身问题，调用方重试可能恢复.
 * 全局异常处理器应记录完整堆栈日志，仅向前端返回脱敏后的错误信息.</p>
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>数据库连接超时或查询失败</li>
 *   <li>Redis 不可达导致缓存操作失败</li>
 *   <li>文件系统读写异常</li>
 *   <li>第三方 API 调用失败（非业务拒绝）</li>
 *   <li>配置缺失导致服务无法启动</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * try {
 *     redisUtil.set("key", value);
 * } catch (RedisException e) {
 *     throw new SystemException("SYS_REDIS", "缓存服务暂不可用", e);
 * }
 * }</pre>
 *
 * @see LetoolException
 * @see BusinessException
 */
public class SystemException extends LetoolException {

    /**
     * 创建系统异常.
     *
     * @param errorCode 系统错误码（如 "SYS_DB"）
     * @param message   错误描述（建议不暴露内部技术细节给前端）
     */
    public SystemException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建包裹原始异常的系统异常.
     *
     * @param errorCode 系统错误码
     * @param message   错误描述
     * @param cause     原始异常（用于日志排查，不应直接返回给前端）
     */
    public SystemException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
