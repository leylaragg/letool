package com.github.leyland.letool.job.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 {@link LetoolJob} 任务 Bean 中唯一的业务处理方法。
 *
 * <p>处理方法必须为公开 {@code void} 方法，并且只能没有参数或接收唯一一个
 * {@link com.github.leyland.letool.job.core.JobContext} 参数。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JobHandler {
}
