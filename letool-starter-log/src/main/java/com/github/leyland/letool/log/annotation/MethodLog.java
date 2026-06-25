package com.github.leyland.letool.log.annotation;

import java.lang.annotation.*;

/**
 * 方法日志注解 —— 标记在需要自动记录入参/出参/耗时/异常的方法上.
 *
 * <h3>记录内容</h3>
 * <ul>
 *   <li>调用类名 + 方法名</li>
 *   <li>入参数组（Object[] toString）</li>
 *   <li>出参（toString，超长截断）</li>
 *   <li>执行耗时（毫秒）</li>
 *   <li>异常信息（异常时）</li>
 * </ul>
 *
 * <h3>典型场景</h3>
 * <pre>{@code
 * // 记录全部信息
 * @MethodLog
 * public Order createOrder(OrderRequest req) { ... }
 *
 * // 只记录耗时和异常，不记录入参出参（参数包含敏感数据）
 * @MethodLog(logArgs = false, logResult = false)
 * public void resetPassword(Long userId, String newPassword) { ... }
 *
 * // 自定义标题 + 限制出参长度
 * @MethodLog(value = "创建订单", maxResultLength = 200)
 * public Order createOrder(OrderRequest req) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodLog {

    /**
     * 日志标题 —— 展示在日志中的操作描述。
     * 为空时默认使用目标方法名。
     */
    String value() default "";

    /**
     * 是否记录入参 —— 默认 true。
     * 参数包含密码/Token/文件流时建议关闭。
     */
    boolean logArgs() default true;

    /**
     * 是否记录出参 —— 默认 true。
     * 返回体过大或含敏感数据时建议关闭。
     */
    boolean logResult() default true;

    /**
     * 出参最大长度（字符数）—— 默认 500。
     * 超出部分截断并追加 "..."，避免日志爆炸。
     */
    int maxResultLength() default 500;

    /**
     * 是否记录异常 —— 默认 true。
     * 关闭后异常不会输出 ERROR 日志（但异常仍会向上抛给调用方）。
     */
    boolean logException() default true;
}
