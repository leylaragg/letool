package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 目录的可测量展示参数，全部长度为排版点。
 *
 * @param titleBaseline 首页面目录标题基线距页顶的距离
 * @param firstEntryBaseline 首个目录条目基线距页顶的距离，必须大于标题基线
 * @param continuationBaseline 续页首个目录条目基线距页顶的距离
 * @param groupHeight 一级条目的基础行高，必须为正数
 * @param entryHeight 其余条目的基础行高，必须为正数
 * @param levelIndent 每增加一级时的左缩进
 * @param pageInset 页码区域距目录行右边界的内缩
 * @param leaderStart 点线起点距标签末端的间隔
 * @param leaderEnd 点线终点距页码起点的间隔
 * @param leaderOffset 点线相对文字基线向上的偏移
 * @param leaderWidth 点线宽度；0 表示不绘制点线
 * @param leaderDash 点线中实线段的长度，必须为正数
 * @param leaderGap 点线中间隔的长度，必须为正数
 * @param groupRuleOffset 一级条目横线相对文字基线向下的偏移
 * @param groupRuleWidth 一级条目横线宽度；0 表示不绘制横线
 * @param groupRuleColor 一级条目横线颜色
 * @param textStyleName 目录条目文本样式名；null 或空字符串使用默认样式
 * @author leyland
 * @date 2026/10/14
 */
public record TableOfContentsLayout(double titleBaseline, double firstEntryBaseline, double continuationBaseline,
    double groupHeight, double entryHeight, double levelIndent, double pageInset, double leaderStart,
    double leaderEnd, double leaderOffset, double leaderWidth, double leaderDash, double leaderGap,
    double groupRuleOffset, double groupRuleWidth, DocumentColor groupRuleColor, String textStyleName) {
    /**
     * 创建并校验目录测量布局。
     *
     * @param titleBaseline 首页面目录标题基线距页顶的距离
     * @param firstEntryBaseline 首个目录条目基线距页顶的距离，必须大于标题基线
     * @param continuationBaseline 续页首个目录条目基线距页顶的距离
     * @param groupHeight 一级条目的基础行高，必须为正数
     * @param entryHeight 其余条目的基础行高，必须为正数
     * @param levelIndent 每增加一级时的左缩进
     * @param pageInset 页码区域距目录行右边界的内缩
     * @param leaderStart 点线起点距标签末端的间隔
     * @param leaderEnd 点线终点距页码起点的间隔
     * @param leaderOffset 点线相对文字基线向上的偏移
     * @param leaderWidth 点线宽度；0 表示不绘制点线
     * @param leaderDash 点线中实线段的长度，必须为正数
     * @param leaderGap 点线中间隔的长度，必须为正数
     * @param groupRuleOffset 一级条目横线相对文字基线向下的偏移
     * @param groupRuleWidth 一级条目横线宽度；0 表示不绘制横线
     * @param groupRuleColor 一级条目横线颜色
     * @param textStyleName 目录条目文本样式名；null 或空字符串使用默认样式
     * @throws NullPointerException 一级条目横线颜色为 null
     * @throws PrintValidationException 长度非有限、超出 0 到 2000 点或基线和行高约束不合法
     */
    public TableOfContentsLayout {
        // 先统一校验物理长度，再校验行高和基线之间的关系。
        for (double value : new double[] {titleBaseline, firstEntryBaseline, continuationBaseline, groupHeight,
                entryHeight, levelIndent, pageInset, leaderStart, leaderEnd, leaderOffset, leaderWidth,
                leaderDash, leaderGap, groupRuleOffset, groupRuleWidth}) {
            if (!Double.isFinite(value) || value < 0 || value > 2000) {
                throw PrintValidationException.invalidDocument("目录展示长度必须是受控非负点值");
            }
        }

        if (groupHeight <= 0 || entryHeight <= 0 || leaderDash <= 0 || leaderGap <= 0
            || firstEntryBaseline <= titleBaseline) {
            throw PrintValidationException.invalidDocument("目录展示行高、点线或首行基线不合法");
        }
        groupRuleColor = Objects.requireNonNull(groupRuleColor, "groupRuleColor 不能为空");
        textStyleName = textStyleName == null ? "" : textStyleName;
    }
}
