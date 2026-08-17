package io.github.leylaragg.letool.ruleengine.function;

/**
 * 函数实例生命周期与线程模型。
 */
public enum FunctionThreading {
    /** 一个不可变或线程安全实例可被并发共享。 */
    THREAD_SAFE,
    /** 每次函数调用创建独立实例。 */
    INVOCATION_SCOPED
}
