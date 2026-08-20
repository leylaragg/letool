package io.github.leylaragg.letool.print.document.node;

import java.util.Objects;

/**
 * 在当前位置显示文档逻辑总页数的行内节点。
 *
 * @author leyland
 */
public final class PageCountNode implements InlineNode {

    /** 可选文本样式名。 */
    private final String styleName;

    /**
     * 创建总页数节点。
     *
     * @param styleName 文本样式名；空字符串表示继承段落默认样式
     */
    public PageCountNode(String styleName) {
        this.styleName = styleName == null ? "" : styleName;
    }

    /** @return 文本样式名 */
    public String styleName() {
        return styleName;
    }

    /** @return 空字符串，总页数节点不参与逻辑定位 */
    @Override
    public String id() {
        return "";
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof PageCountNode that
                && styleName.equals(that.styleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(styleName);
    }
}
