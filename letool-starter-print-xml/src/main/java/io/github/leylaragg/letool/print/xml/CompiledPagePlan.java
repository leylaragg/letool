package io.github.leylaragg.letool.print.xml;

import io.github.leylaragg.letool.print.document.PageLayout;
import io.github.leylaragg.letool.print.document.PageNumbering;

import java.util.List;
import java.util.Objects;

/**
 * 一个页面序列已经冻结的布局、页码和三个内容区域。
 *
 * @author leyland
 */
final class CompiledPagePlan {

    /** 页面物理布局。 */
    private final PageLayout pageLayout;

    /** 页面序列逻辑页码规则。 */
    private final PageNumbering pageNumbering;

    /** 页眉块计划。 */
    private final List<CompiledXmlNode> header;

    /** 正文块计划。 */
    private final List<CompiledXmlNode> body;

    /** 页脚块计划。 */
    private final List<CompiledXmlNode> footer;

    /** 保存一个可并发绑定的页面序列计划。 */
    CompiledPagePlan(PageLayout pageLayout, PageNumbering pageNumbering,
            List<CompiledXmlNode> header, List<CompiledXmlNode> body,
            List<CompiledXmlNode> footer) {
        this.pageLayout = Objects.requireNonNull(pageLayout, "pageLayout 不能为空");
        this.pageNumbering = Objects.requireNonNull(pageNumbering, "pageNumbering 不能为空");
        this.header = List.copyOf(header);
        this.body = List.copyOf(body);
        this.footer = List.copyOf(footer);
    }

    /** @return 页面物理布局 */
    PageLayout pageLayout() {
        return pageLayout;
    }

    /** @return 页面序列逻辑页码规则 */
    PageNumbering pageNumbering() {
        return pageNumbering;
    }

    /** @return 页眉块计划 */
    List<CompiledXmlNode> header() {
        return header;
    }

    /** @return 正文块计划 */
    List<CompiledXmlNode> body() {
        return body;
    }

    /** @return 页脚块计划 */
    List<CompiledXmlNode> footer() {
        return footer;
    }
}
