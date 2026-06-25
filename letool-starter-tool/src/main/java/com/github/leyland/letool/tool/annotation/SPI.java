package com.github.leyland.letool.tool.annotation;

import java.lang.annotation.*;

/**
 * SPI（Service Provider Interface）扩展点标记——标记接口或抽象类为可扩展的服务提供者接口.
 *
 * <h3>设计意图</h3>
 * <p>Java 原生 SPI 机制（{@link java.util.ServiceLoader}）需要在 {@code META-INF/services/} 下
 * 手动注册实现类，缺乏可发现性。此注解配合 {@link com.github.leyland.letool.tool.util.ClassUtil ClassUtil}
 * 的类扫描能力，可自动发现实现类.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 定义扩展点接口
 * @SPI
 * public interface DataHandler {
 *     void handle(Object data);
 * }
 *
 * // 实现扩展点
 * public class JsonDataHandler implements DataHandler { ... }
 *
 * // 自动发现所有实现类
 * List<Class<?>> impls = ClassUtil.scanByInterface("com.example", DataHandler.class);
 * }</pre>
 *
 * <p>注意：此注解保留到运行时（{@link RetentionPolicy#RUNTIME}），以便扫描器读取.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SPI {
    /** 可选的扩展点名（如 "default" 表示默认实现） */
    String value() default "";
}
