package com.github.leyland.letool.web.annotation;

import java.lang.annotation.*;

/**
 * 排除响应包装 —— 标注在 Controller 方法或类上，返回时不自动包装为 {@code R<T>}.
 *
 * <p>适用于文件下载、回调接口等不需要统一响应体的场景.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcludeWrapper {
}
