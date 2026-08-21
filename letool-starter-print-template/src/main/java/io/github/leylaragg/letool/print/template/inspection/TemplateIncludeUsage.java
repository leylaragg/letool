package io.github.leylaragg.letool.print.template.inspection;

import java.util.Map;
import java.util.Objects;

/**
 * 一个 include 调用及其显式参数映射。
 *
 * @author leyland
 */
public final class TemplateIncludeUsage {

    /** 发起调用的模板代码。 */
    private final String sourceTemplateCode;

    /** 被引用的片段模板代码。 */
    private final String targetTemplateCode;

    /** 参数名到调用方路径的有序映射。 */
    private final Map<String, String> arguments;

    /** include 出现的安全位置。 */
    private final TemplateSourceLocation location;

    /**
     * 保存一个片段调用。
     *
     * @param sourceTemplateCode 调用方模板代码
     * @param targetTemplateCode 目标片段模板代码
     * @param arguments 参数名到调用方路径的映射
     * @param location 安全源码位置
     */
    public TemplateIncludeUsage(String sourceTemplateCode, String targetTemplateCode,
            Map<String, String> arguments, TemplateSourceLocation location) {
        this.sourceTemplateCode = InspectionValues.templateCode(
                sourceTemplateCode, "sourceTemplateCode");
        this.targetTemplateCode = InspectionValues.templateCode(
                targetTemplateCode, "targetTemplateCode");
        this.arguments = InspectionValues.includeArguments(arguments);
        this.location = Objects.requireNonNull(location, "location 不能为空");
    }

    /** @return 调用方模板代码 */
    public String sourceTemplateCode() {
        return sourceTemplateCode;
    }

    /** @return 目标片段模板代码 */
    public String targetTemplateCode() {
        return targetTemplateCode;
    }

    /** @return 参数名到调用方路径的不可修改映射 */
    public Map<String, String> arguments() {
        return arguments;
    }

    /** @return include 出现的安全位置 */
    public TemplateSourceLocation location() {
        return location;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TemplateIncludeUsage that)) {
            return false;
        }
        return sourceTemplateCode.equals(that.sourceTemplateCode)
                && targetTemplateCode.equals(that.targetTemplateCode)
                && arguments.equals(that.arguments) && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceTemplateCode, targetTemplateCode, arguments, location);
    }
}
