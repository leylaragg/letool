package com.github.leyland.letool.sensitive.core;

/**
 * 脱敏策略接口 —— 定义单一脱敏规则的算法契约，所有内置和自定义脱敏策略必须实现此接口.
 *
 * <h3>契约约定</h3>
 * <ul>
 *   <li><b>null 安全</b>：传入 null 或空字符串时返回空字符串或 null，禁止抛 NPE</li>
 *   <li><b>无状态</b>：策略实例不得持有可变状态，保证多线程并发调用的安全性</li>
 *   <li><b>幂等</b>：对同一输入多次调用返回相同结果</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>Spring 容器启动时，{@code @Component} 标注的实现类自动注册到 SensitiveProcessor</li>
 *   <li>也可通过 {@code SensitiveProcessor.register(type, strategy)} 手动注册</li>
 *   <li>策略实例全局唯一，注册后不被 GC 回收</li>
 * </ol>
 *
 * @param <C> 上下文类型 —— 内置策略使用 {@link MaskContext}（含 keepPrefix/keepSuffix/maskChar/pattern/replacement），
 *            自定义策略可使用 Void 或自定义上下文类型。
 */
@FunctionalInterface
public interface SensitiveStrategy<C> {

    /**
     * 对单条敏感数据进行脱敏处理.
     *
     * @param value   原始明文值，可能为 null 或空字符串
     * @param context 脱敏上下文 —— 包含保留前后缀长度、遮盖字符、自定义正则等配置参数
     * @return 脱敏后的值，null 输入通常返回 null 或 ""
     */
    String mask(String value, C context);
}
