package com.github.leyland.letool.web.version;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * 支持 {@link ApiVersion} 的请求映射处理器。
 *
 * <p>该处理器在 Spring MVC 构建接口映射时读取控制器类和处理方法上的
 * {@code @ApiVersion} 注解，并转换为 {@link ApiVersionRequestMapping}
 * 自定义匹配条件。这样同一路径、同一 HTTP 方法可以通过请求中的版本参数
 * 路由到不同处理方法。</p>
 */
public class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    /**
     * 读取控制器类上的版本声明，作为该类下所有接口的默认版本条件。
     *
     * <p>使用 {@link AnnotatedElementUtils#findMergedAnnotation} 可以兼容组合注解、
     * 元注解以及 Spring 的注解合并规则。</p>
     *
     * @param handlerType 控制器类型
     * @return 类级别 API 版本匹配条件；未声明版本时返回 {@code null}
     */
    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        ApiVersion apiVersion = AnnotatedElementUtils.findMergedAnnotation(handlerType, ApiVersion.class);
        return createCondition(apiVersion);
    }

    /**
     * 读取处理方法上的版本声明，作为具体接口的版本匹配条件。
     *
     * <p>方法级条件会参与 Spring MVC 的请求匹配和最佳映射排序，用于区分
     * 相同路径下的不同 API 版本。</p>
     *
     * @param method 控制器处理方法
     * @return 方法级 API 版本匹配条件；未声明版本时返回 {@code null}
     */
    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        ApiVersion apiVersion = AnnotatedElementUtils.findMergedAnnotation(method, ApiVersion.class);
        return createCondition(apiVersion);
    }

    /**
     * 将注解声明转换为 Spring MVC 可识别的请求条件。
     *
     * @param apiVersion API 版本注解实例
     * @return 对应的版本匹配条件；注解不存在时返回 {@code null}，表示不附加自定义条件
     */
    private RequestCondition<?> createCondition(ApiVersion apiVersion) {
        return apiVersion != null ? new ApiVersionRequestMapping(apiVersion.value()) : null;
    }
}
