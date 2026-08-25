package io.github.leylaragg.letool.ruleengine.compile;

/**
 * 表示编译产物记录的事实依赖能否覆盖求值期间的全部事实读取。
 */
public enum DependencyCoverage {

    /** 所有事实读取都来自表达式中的显式路径或函数显式参数。 */
    COMPLETE,

    /** 至少一个函数可能从调用上下文读取显式参数以外的事实。 */
    DYNAMIC
}
