package com.github.leyland.letool.print.spel;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.IndexAccessor;
import org.springframework.expression.TypedValue;

/**
 * 只允许 SpEL 按非负整数读取内部 JSON 数组的无状态访问器。
 *
 * <p>访问器不保存目标节点或求值状态，可以安全并发复用。</p>
 *
 * @author leyland
 */
final class RestrictedSpelIndexAccessor implements IndexAccessor {

    /** 访问器唯一支持的内部目标类型。 */
    private static final Class<?>[] TARGET_TYPES = {RestrictedSpelDataNode.class};

    /**
     * 限定访问器只处理框架内部数据节点。
     *
     * @return 支持的内部目标类型
     */
    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return TARGET_TYPES.clone();
    }

    /**
     * 判断当前目标和下标是否可以安全读取。
     *
     * @param context Spring 求值上下文
     * @param target 当前读取目标
     * @param index 下标对象
     * @return 下标是否为当前数组的有效非负整数
     */
    @Override
    public boolean canRead(EvaluationContext context, Object target, Object index) {
        return target instanceof RestrictedSpelDataNode dataNode
                && dataNode.hasIndex(index);
    }

    /**
     * 读取内部 JSON 数组下标。
     *
     * @param context Spring 求值上下文
     * @param target 当前读取目标
     * @param index 下标对象
     * @return 数组元素值
     * @throws AccessException 目标或下标不符合只读数组契约时抛出
     */
    @Override
    public TypedValue read(EvaluationContext context, Object target, Object index)
            throws AccessException {
        if (!(target instanceof RestrictedSpelDataNode dataNode)) {
            throw new AccessException("不支持的数组读取目标");
        }
        try {
            return new TypedValue(dataNode.readIndex(index));
        } catch (IllegalArgumentException exception) {
            throw new AccessException("数组读取失败", exception);
        }
    }

    /**
     * 受限数据视图永远不允许下标写入。
     *
     * @param context Spring 求值上下文
     * @param target 当前写入目标
     * @param index 下标对象
     * @return 固定返回 {@code false}
     */
    @Override
    public boolean canWrite(EvaluationContext context, Object target, Object index) {
        return false;
    }

    /**
     * 拒绝任何数组下标写入请求。
     *
     * @param context Spring 求值上下文
     * @param target 当前写入目标
     * @param index 下标对象
     * @param newValue 待写入值
     * @throws AccessException 始终抛出，表示只读边界
     */
    @Override
    public void write(
            EvaluationContext context, Object target, Object index, Object newValue)
            throws AccessException {
        throw new AccessException("受限表达式数据只读");
    }
}
