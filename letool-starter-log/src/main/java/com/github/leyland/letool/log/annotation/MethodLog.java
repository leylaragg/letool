package com.github.leyland.letool.log.annotation;

import java.lang.annotation.*;

/**
 * 方法日志注解 —— 标记在需要自动记录执行状态、耗时和异常的方法上。
 *
 * <h2>记录内容</h2>
 * <ul>
 *   <li>调用类名 + 方法名</li>
 *   <li>入参数组（显式开启后使用可替换的 JSON 编解码器序列化）</li>
 *   <li>出参（显式开启后使用可替换的 JSON 编解码器序列化）</li>
 *   <li>执行耗时（毫秒）</li>
 *   <li>异常信息（异常时）</li>
 * </ul>
 *
 * <h2>典型场景</h2>
 * <pre>{@code
 * // 显式记录入参与出参
 * @MethodLog(logArgs = true, logResult = true)
 * public Order createOrder(OrderRequest req) { ... }
 *
 * // 默认只记录执行结果、耗时和异常，不采集入参与出参
 * @MethodLog
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
     *
     * @return 日志标题
     */
    String value() default "";

    /**
     * 是否记录入参 —— 默认关闭。
     * 确认参数不包含密码、Token、文件流等敏感或不可序列化数据后再显式开启。
     *
     * @return {@code true} 表示记录方法参数
     */
    boolean logArgs() default false;

    /**
     * 是否记录出参 —— 默认关闭。
     * 确认返回值不包含敏感数据且体积可控后再显式开启。
     *
     * @return {@code true} 表示记录方法返回值
     */
    boolean logResult() default false;

    /**
     * 入参最大长度（字符数）—— 默认 500。
     * 超出部分截断并追加 "..."，避免大集合或复杂对象占用过多日志空间。
     *
     * @return 入参最大记录长度
     */
    int maxArgsLength() default 500;

    /**
     * 出参最大长度（字符数）—— 默认 500。
     * 超出部分截断并追加 "..."，避免日志爆炸。
     *
     * @return 返回值最大记录长度
     */
    int maxResultLength() default 500;

    /**
     * 是否记录异常 —— 默认 true。
     * 关闭后异常不会输出 ERROR 日志（但异常仍会向上抛给调用方）。
     *
     * @return {@code true} 表示记录异常日志
     */
    boolean logException() default true;
}
