package io.github.leylaragg.letool.web.version;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * 将 {@link ApiVersion} 转换为 Spring MVC 自定义请求条件的处理器映射。
 *
 * <p>类级版本作为默认条件，方法级版本通过 Spring 的条件合并语义覆盖类级版本。
 * 非法注解值会在 Controller 映射初始化阶段失败，并在错误消息中包含声明位置。</p>
 */
public class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    /** 版本请求头名称。 */
    private final String headerName;

    /** 版本查询参数名称。 */
    private final String parameterName;

    /**
     * 创建 API 版本处理器映射。
     *
     * @param headerName 非空白版本请求头名称
     * @param parameterName 非空白版本查询参数名称
     * @throws IllegalArgumentException 当名称为空白时抛出
     */
    public ApiVersionRequestMappingHandlerMapping(String headerName, String parameterName) {
        this.headerName = requireName(headerName, "headerName");
        this.parameterName = requireName(parameterName, "parameterName");
    }

    /**
     * 读取 Controller 类上的默认版本声明。
     *
     * @param handlerType Controller 类型
     * @return 类级版本条件；未声明时返回 {@code null}
     */
    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        ApiVersion apiVersion = AnnotatedElementUtils.findMergedAnnotation(handlerType, ApiVersion.class);
        return createCondition(apiVersion, handlerType.getName());
    }

    /**
     * 读取 Controller 方法上的版本声明。
     *
     * @param method Controller 处理方法
     * @return 方法级版本条件；未声明时返回 {@code null}
     */
    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        ApiVersion apiVersion = AnnotatedElementUtils.findMergedAnnotation(method, ApiVersion.class);
        return createCondition(apiVersion, method.toGenericString());
    }

    /**
     * 将注解声明转换为 Spring MVC 请求条件。
     *
     * @param apiVersion API 版本注解
     * @param declaration 注解声明位置描述
     * @return API 版本条件；注解不存在时返回 {@code null}
     * @throws IllegalStateException 当注解版本不是正整数时抛出
     */
    private RequestCondition<?> createCondition(ApiVersion apiVersion, String declaration) {
        if (apiVersion == null) {
            return null;
        }
        if (apiVersion.value() <= 0) {
            throw new IllegalStateException(
                    "@ApiVersion 主版本必须大于 0，声明位置: " + declaration);
        }
        return new ApiVersionRequestMapping(apiVersion.value(), headerName, parameterName);
    }

    /**
     * 校验并规范化版本来源名称。
     *
     * @param value 原始名称
     * @param argumentName 参数名称
     * @return 去除首尾空白的名称
     */
    private static String requireName(String value, String argumentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(argumentName + " must not be blank");
        }
        return value.strip();
    }
}
