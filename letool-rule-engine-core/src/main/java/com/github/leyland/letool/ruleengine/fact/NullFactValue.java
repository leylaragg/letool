package com.github.leyland.letool.ruleengine.fact;

/**
 * 空值事实的无状态单例。
 */
public final class NullFactValue implements FactValue {

    private static final NullFactValue INSTANCE = new NullFactValue();

    private NullFactValue() {
    }

    /**
     * 共享的空事实值单例。
     *
     * @return 空值事实
     */
    public static NullFactValue instance() {
        return INSTANCE;
    }

    @Override
    public FactKind kind() {
        return FactKind.NULL;
    }

    @Override
    public Object toSafeJavaValue() {
        return null;
    }

    @Override
    public String toString() {
        return "null";
    }
}
