package io.github.leylaragg.letool.ruleengine.function;

/**
 * 为调用级隔离函数创建执行实例的工厂。
 */
public interface RuleFunctionFactory {

    /**
     * 注册时可安全冻结、且不包含执行实例的函数描述。
     *
     * @return 函数描述符
     */
    FunctionDescriptor descriptor();

    /**
     * 创建全新单次调用函数实例。
     *
     * @return 新函数实例
     */
    RuleFunction create();
}
