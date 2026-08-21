package io.github.leylaragg.letool.print.template.inspection;

import java.util.Objects;
import java.util.Set;

/**
 * 一次静态数据路径读取及其词法作用域。
 *
 * @author leyland
 */
public final class TemplatePathUsage {

    /** 模板前端已经校验的数据路径。 */
    private final String dataPath;

    /** 读取该路径的用途。 */
    private final TemplatePathUsageKind kind;

    /** 当前可见循环变量。 */
    private final Set<String> visibleVariables;

    /** 当前可见片段参数。 */
    private final Set<String> fragmentParameters;

    /** 路径出现的安全位置。 */
    private final TemplateSourceLocation location;

    /**
     * 保存一次路径使用。
     *
     * @param dataPath 经过模板前端校验的数据路径
     * @param kind 路径用途
     * @param visibleVariables 当前可见循环变量
     * @param fragmentParameters 当前可见片段参数
     * @param location 安全源码位置
     */
    public TemplatePathUsage(String dataPath, TemplatePathUsageKind kind,
            Set<String> visibleVariables, Set<String> fragmentParameters,
            TemplateSourceLocation location) {
        this.dataPath = InspectionValues.dataPath(dataPath);
        this.kind = Objects.requireNonNull(kind, "kind 不能为空");
        this.visibleVariables = InspectionValues.orderedNames(
                visibleVariables, true, "visibleVariable");
        this.fragmentParameters = InspectionValues.orderedNames(
                fragmentParameters, true, "fragmentParameter");
        this.location = Objects.requireNonNull(location, "location 不能为空");
    }

    /** @return 模板前端已经校验的数据路径 */
    public String dataPath() {
        return dataPath;
    }

    /** @return 路径用途 */
    public TemplatePathUsageKind kind() {
        return kind;
    }

    /** @return 当前可见循环变量 */
    public Set<String> visibleVariables() {
        return visibleVariables;
    }

    /** @return 当前可见片段参数 */
    public Set<String> fragmentParameters() {
        return fragmentParameters;
    }

    /** @return 路径出现的安全位置 */
    public TemplateSourceLocation location() {
        return location;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TemplatePathUsage that)) {
            return false;
        }
        return dataPath.equals(that.dataPath) && kind == that.kind
                && visibleVariables.equals(that.visibleVariables)
                && fragmentParameters.equals(that.fragmentParameters)
                && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataPath, kind, visibleVariables, fragmentParameters, location);
    }
}
