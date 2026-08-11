package com.github.leyland.letool.tool.enums;

/**
 * 约定业务枚举提供稳定编码的轻量契约。
 *
 * <p>实现该接口后，{@code EnumUtil} 可以直接读取编码而无需反射。编码类型由业务枚举自行决定，
 * 适用于数据库值、接口协议值和规则标识等场景。</p>
 *
 * @param <C> 业务编码类型
 */
public interface CodeEnum<C> {

    /**
     * 获取稳定的业务编码。
     *
     * @return 业务编码
     */
    C getCode();
}
