package com.github.leyland.letool.ai.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 函数调用注解 —— 标记一个方法为可被大模型调用的 Function.
 *
 * <h3>使用场景</h3>
 * <p>在 Spring Bean 的方法上添加此注解后，可通过
 * {@link com.github.leyland.letool.ai.function.FunctionCallHandler} 自动注册
 * 该方法的函数定义信息（名称、描述），供大模型在 Function Calling 中使用.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @AiFunction(name = "get_weather", description = "获取指定城市的天气信息")
 * public String getWeather(String city) {
 *     return weatherService.query(city);
 * }
 * }</pre>
 *
 * <h3>配合 FunctionCallHandler</h3>
 * <p>方法签名必须为 {@code Object -> String} 或 {@code Map<String, Object> -> String}，
 * 因为大模型传递的参数将被解析为 {@code Map<String, Object>}.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiFunction {

    /**
     * 函数名称 —— 大模型根据此名称决定调用哪个函数.
     *
     * @return 函数名称，必须全局唯一
     */
    String name();

    /**
     * 函数描述 —— 详细说明函数的功能、参数和返回值.
     *
     * <p>大模型会根据此描述判断是否应该调用该函数以及如何传参.
     * 描述越清晰，大模型调用的准确率越高.</p>
     *
     * @return 函数的功能描述字符串
     */
    String description();
}
