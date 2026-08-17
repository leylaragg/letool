package io.github.leylaragg.letool.print.service;

import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 连接业务定义编码、模板代码和数据适配器的不可变打印定义。
 *
 * @param <R> 适配器接受的业务请求类型
 * @author leyland
 */
public final class PrintDefinition<R> {

    /** 业务定义编码使用稳定的小写安全格式。 */
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9._-]{0,127}");

    /** 业务调用使用的稳定定义编码。 */
    private final String code;

    /** 模板集合中的文档模板代码。 */
    private final String templateCode;

    /** 运行时校验请求的可信 Java 类型。 */
    private final Class<R> requestType;

    /** 将业务请求转换为只读打印上下文。 */
    private final PrintDataAdapter<R> adapter;

    /**
     * @param code 稳定业务定义编码
     * @param templateCode 文档模板代码
     * @param requestType 可信请求类型
     * @param adapter 无状态数据适配器
     */
    private PrintDefinition(
            String code,
            String templateCode,
            Class<R> requestType,
            PrintDataAdapter<R> adapter) {
        this.code = requireCode(code);
        if (templateCode == null || templateCode.isBlank() || templateCode.length() > 128) {
            throw new IllegalArgumentException("templateCode 必须为不超过 128 个字符的非空白文本");
        }
        this.templateCode = templateCode;
        this.requestType = Objects.requireNonNull(requestType, "requestType 不能为空");
        this.adapter = Objects.requireNonNull(adapter, "adapter 不能为空");
    }

    /**
     * 创建由宿主声明的业务打印定义。
     *
     * @param code 稳定小写定义编码
     * @param templateCode 模板集合中的文档代码
     * @param requestType 可信 Java 请求类型
     * @param adapter 无共享请求状态的数据适配器
     * @param <R> 请求类型
     * @return 不可变业务打印定义
     */
    public static <R> PrintDefinition<R> of(
            String code,
            String templateCode,
            Class<R> requestType,
            PrintDataAdapter<R> adapter) {
        return new PrintDefinition<>(code, templateCode, requestType, adapter);
    }

    /** @return 稳定业务定义编码 */
    public String code() {
        return code;
    }

    /** @return 模板集合中的文档代码 */
    public String templateCode() {
        return templateCode;
    }

    /** @return 可信 Java 请求类型 */
    public Class<R> requestType() {
        return requestType;
    }

    /**
     * 校验请求类型后调用宿主数据适配器。
     *
     * @param request 业务调用传入的请求
     * @return 适配器生成的上下文，可以为空并由门面统一拒绝
     * @throws PrintValidationException 请求为空或类型不匹配时抛出
     */
    PrintContext load(Object request) {
        if (request == null || !requestType.isInstance(request)) {
            throw PrintValidationException.invalidRequest("业务打印请求类型不匹配");
        }
        return adapter.load(requestType.cast(request));
    }

    /**
     * @param code 待校验业务定义编码
     * @return 格式稳定的小写编码
     */
    private static String requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("code 必须是稳定的小写安全标识");
        }
        return code;
    }
}
