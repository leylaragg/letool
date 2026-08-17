package io.github.leylaragg.letool.sensitive.core;

/**
 * 定义单一脱敏规则的算法契约。
 *
 * <h3>契约约定</h3>
 * <ul>
 *   <li><b>null 安全</b>：传入 null 或空字符串时返回空字符串或 null，禁止抛 NPE</li>
 *   <li><b>无状态</b>：策略实例不得持有可变状态，保证多线程并发调用的安全性</li>
 *   <li><b>幂等</b>：对同一输入多次调用返回相同结果</li>
 * </ul>
 *
 * <p>自定义策略通过 {@link SensitiveStrategyRegistry#builder()} 注册，并将构建结果声明为
 * Spring Bean。策略注册表不可变，因此策略实现本身也应保持无状态和线程安全。</p>
 *
 * @param <C> 上下文类型；模块注册表使用 {@link MaskContext}
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
