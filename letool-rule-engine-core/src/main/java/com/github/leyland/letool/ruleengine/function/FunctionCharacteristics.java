package com.github.leyland.letool.ruleengine.function;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;

import java.util.Objects;

/**
 * 决定函数语义与执行隔离方式的不可变特征集合。
 */
public final class FunctionCharacteristics {

    /** 相同输入是否保证相同结果。 */
    private final FunctionDeterminism determinism;

    /** 函数允许读取的状态范围。 */
    private final FunctionEffect effect;

    /** 函数实例的共享或调用级隔离模型。 */
    private final FunctionThreading threading;

    /** 接收完整且非空的函数治理特征。 */
    private FunctionCharacteristics(
            FunctionDeterminism determinism,
            FunctionEffect effect,
            FunctionThreading threading) {
        if (determinism == null || effect == null || threading == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.determinism = determinism;
        this.effect = effect;
        this.threading = threading;
    }

    /**
     * 创建函数特征。
     *
     * @param determinism 结果确定性
     * @param effect 状态读取范围
     * @param threading 实例线程模型
     * @return 不可变特征
     */
    public static FunctionCharacteristics of(
            FunctionDeterminism determinism,
            FunctionEffect effect,
            FunctionThreading threading) {
        return new FunctionCharacteristics(determinism, effect, threading);
    }

    /**
     * 参与函数语义目录的确定性特征。
     *
     * @return 确定性
     */
    public FunctionDeterminism determinism() {
        return determinism;
    }

    /**
     * 参与执行治理的状态读取范围。
     *
     * @return 状态读取范围
     */
    public FunctionEffect effect() {
        return effect;
    }

    /**
     * 注册表选择共享实例或调用级工厂所需的线程模型。
     *
     * @return 线程模型
     */
    public FunctionThreading threading() {
        return threading;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FunctionCharacteristics that)) return false;
        return determinism == that.determinism && effect == that.effect
                && threading == that.threading;
    }

    @Override
    public int hashCode() {
        return Objects.hash(determinism, effect, threading);
    }

    @Override
    public String toString() {
        return determinism + "/" + effect + "/" + threading;
    }
}
