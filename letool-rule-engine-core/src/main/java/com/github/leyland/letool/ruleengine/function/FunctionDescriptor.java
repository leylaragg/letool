package com.github.leyland.letool.ruleengine.function;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 不含执行实例的不可变函数目录元数据。
 */
public final class FunctionDescriptor {

    /** 宿主函数编码的 ASCII 输入格式。 */
    private static final Pattern RAW_CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");

    /** 函数语义版本允许的稳定格式。 */
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    /** 规范化为大写的函数编码。 */
    private final String code;

    /** 宿主声明的函数语义版本。 */
    private final String semanticVersion;

    /** 不可变参数签名。 */
    private final FunctionSignature signature;

    /** 函数返回类型。 */
    private final TypeDescriptor returnType;

    /** 确定性、副作用和线程模型元数据。 */
    private final FunctionCharacteristics characteristics;

    /** 接收并冻结完整函数元数据。 */
    private FunctionDescriptor(
            String code,
            String semanticVersion,
            FunctionSignature signature,
            TypeDescriptor returnType,
            FunctionCharacteristics characteristics) {
        this.code = normalizeCode(code);
        if (semanticVersion == null || !VERSION_PATTERN.matcher(semanticVersion).matches()
                || signature == null || returnType == null || characteristics == null) {
            throw RuleEngineException.invalidArgument();
        }
        this.semanticVersion = semanticVersion;
        this.signature = signature;
        this.returnType = returnType;
        this.characteristics = characteristics;
    }

    /**
     * 创建函数描述符。
     *
     * @param code 函数编码
     * @param semanticVersion 语义版本
     * @param signature 参数签名
     * @param returnType 返回类型
     * @param characteristics 函数特征
     * @return 规范化不可变描述符
     */
    public static FunctionDescriptor of(
            String code,
            String semanticVersion,
            FunctionSignature signature,
            TypeDescriptor returnType,
            FunctionCharacteristics characteristics) {
        return new FunctionDescriptor(
                code, semanticVersion, signature, returnType, characteristics);
    }

    /**
     * 从函数公开元数据创建描述符。
     *
     * @param function 函数实例
     * @return 函数描述符
     */
    public static FunctionDescriptor from(RuleFunction function) {
        if (function == null) throw RuleEngineException.invalidArgument();
        try {
            return of(function.code(), function.semanticVersion(), function.signature(),
                    function.returnType(), function.characteristics());
        } catch (RuntimeException exception) {
            throw RuleEngineException.invalidArgument();
        }
    }

    /**
     * 用于目录索引和表达式调用的规范大写编码。
     *
     * @return 函数编码
     */
    public String code() {
        return code;
    }

    /**
     * 参与函数目录指纹的语义版本。
     *
     * @return 语义版本
     */
    public String semanticVersion() {
        return semanticVersion;
    }

    /**
     * 编译期实参检查使用的参数签名。
     *
     * @return 参数签名
     */
    public FunctionSignature signature() {
        return signature;
    }

    /**
     * 编译期类型推导使用的返回类型。
     *
     * @return 返回类型
     */
    public TypeDescriptor returnType() {
        return returnType;
    }

    /**
     * 治理执行生命周期所需的函数特征。
     *
     * @return 函数特征
     */
    public FunctionCharacteristics characteristics() {
        return characteristics;
    }

    /** 把受限 ASCII 编码规范化为与区域无关的大写形式。 */
    static String normalizeCode(String code) {
        // 先按 UTF-16 长度做常量时间上界检查，再执行完整 ASCII 结构校验。
        if (code == null || code.length() > 128 || !RAW_CODE_PATTERN.matcher(code).matches()) {
            throw RuleEngineException.invalidArgument();
        }
        return code.toUpperCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FunctionDescriptor that)) return false;
        return code.equals(that.code)
                && semanticVersion.equals(that.semanticVersion)
                && signature.equals(that.signature)
                && returnType.equals(that.returnType)
                && characteristics.equals(that.characteristics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, semanticVersion, signature, returnType, characteristics);
    }

    @Override
    public String toString() {
        return code + "@" + semanticVersion + signature + ":" + returnType;
    }
}
