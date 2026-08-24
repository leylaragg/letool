package io.github.leylaragg.letool.cache.exception;

import io.github.leylaragg.letool.exception.code.ErrorCode;

/**
 * 缓存模块对外暴露的稳定错误码。
 */
public enum CacheErrorCode implements ErrorCode {

    /** 缓存区域或运行参数配置不合法。 */
    CONFIGURATION_INVALID("CACHE_001", "缓存配置不合法：{0}"),

    /** 请求的缓存实例尚未注册。 */
    CACHE_NOT_FOUND("CACHE_002", "缓存实例不存在"),

    /** 缓存未命中后的业务回源执行失败。 */
    LOADER_FAILED("CACHE_003", "缓存回源失败"),

    /** 跨节点缓存失效消息格式不合法。 */
    INVALIDATION_MESSAGE_INVALID("CACHE_004", "缓存失效消息不合法"),

    /** 同一缓存名称被注册为不同的数据结构。 */
    CACHE_TYPE_CONFLICT("CACHE_005", "缓存名称已被其他数据结构占用"),

    /** 需要权威 L2 结果时 Redis 当前不可用。 */
    L2_UNAVAILABLE("CACHE_006", "缓存 L2 当前不可用"),

    /** 自定义序列化器没有实现参数化类型反序列化。 */
    GENERIC_TYPE_UNSUPPORTED("CACHE_007", "缓存序列化器不支持泛型类型：{0}");

    /** 供程序判断和响应映射使用的稳定错误码。 */
    private final String code;

    /** 未配置国际化文案时使用的默认消息。 */
    private final String defaultMessage;

    /**
     * 创建缓存错误码定义。
     *
     * @param code 稳定的机器可读错误码
     * @param defaultMessage 默认的人类可读消息模板
     */
    CacheErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取稳定错误码。
     *
     * @return 非空错误码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取默认错误消息模板。
     *
     * @return 非空默认消息模板
     */
    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
