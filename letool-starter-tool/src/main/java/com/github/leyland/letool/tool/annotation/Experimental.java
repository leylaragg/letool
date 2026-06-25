package com.github.leyland.letool.tool.annotation;

import java.lang.annotation.*;

/**
 * 标记 API 为实验性——可能在后续版本中变更签名或移除，不保证向后兼容.
 *
 * <h3>设计意图</h3>
 * <ul>
 *   <li>给调用方预警：此 API 不稳定，升级版本时需关注变更日志</li>
 *   <li>仅保留在源码级别（{@link RetentionPolicy#SOURCE}），不进入字节码</li>
 *   <li>IDE 可通过插件高亮标记此类 API 的调用</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Experimental("计划在 2.1 版本稳定")
 * public class NewFeatureModule { ... }
 *
 * @Experimental
 * public void experimentalMethod() { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Experimental {
    /** 可选的稳定版本说明 */
    String value() default "";
}
