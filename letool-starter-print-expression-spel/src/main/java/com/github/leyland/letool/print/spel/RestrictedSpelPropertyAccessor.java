package com.github.leyland.letool.print.spel;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

/**
 * 只允许 SpEL 读取内部 JSON 包装节点属性的无状态访问器。
 *
 * <p>访问器不保存目标节点或求值状态，可以安全并发复用。</p>
 *
 * @author leyland
 */
final class RestrictedSpelPropertyAccessor implements PropertyAccessor {

    /** 访问器唯一支持的内部目标类型。 */
    private static final Class<?>[] TARGET_TYPES = {RestrictedSpelDataNode.class};

    /**
     * 限定访问器只处理框架内部数据节点，避免成为任意对象反射入口。
     *
     * @return 支持的内部目标类型
     */
    @Override
    public Class<?>[] getSpecificTargetClasses() {
        return TARGET_TYPES.clone();
    }

    /**
     * 判断目标节点是否存在可读属性。
     *
     * @param context Spring 求值上下文
     * @param target 当前读取目标
     * @param name 属性名称
     * @return 属性是否可读
     */
    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
        return target instanceof RestrictedSpelDataNode dataNode
                && dataNode.hasProperty(name);
    }

    /**
     * 从内部节点读取属性，并包装为 Spring 类型值。
     *
     * @param context Spring 求值上下文
     * @param target 当前读取目标
     * @param name 属性名称
     * @return 属性读取结果
     * @throws AccessException 目标不是内部数据节点或属性不存在时抛出
     */
    @Override
    public TypedValue read(EvaluationContext context, Object target, String name)
            throws AccessException {
        if (!(target instanceof RestrictedSpelDataNode dataNode)) {
            throw new AccessException("不支持的属性读取目标");
        }
        try {
            return new TypedValue(dataNode.readProperty(name));
        } catch (IllegalArgumentException exception) {
            throw new AccessException("属性读取失败", exception);
        }
    }

    /**
     * 受限数据视图永远不允许写入。
     *
     * @param context Spring 求值上下文
     * @param target 当前写入目标
     * @param name 属性名称
     * @return 固定返回 {@code false}
     */
    @Override
    public boolean canWrite(EvaluationContext context, Object target, String name) {
        return false;
    }

    /**
     * 拒绝任何属性写入请求。
     *
     * @param context Spring 求值上下文
     * @param target 当前写入目标
     * @param name 属性名称
     * @param newValue 待写入值
     * @throws AccessException 始终抛出，表示只读边界
     */
    @Override
    public void write(
            EvaluationContext context, Object target, String name, Object newValue)
            throws AccessException {
        throw new AccessException("受限表达式数据只读");
    }
}
