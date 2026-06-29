package com.github.leyland.letool.excel.style;

import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;

/**
 * Excel样式模板工厂。
 *
 * <p>提供预定义的 EasyExcel 样式策略（{@link HorizontalCellStyleStrategy}），
 * 用于统一控制导出Excel的表格外观（表头样式、内容样式、边框、对齐等）。
 *
 * <p>该类为工具类（final + private构造器），不可实例化，仅提供静态工厂方法。
 * 样式策略可以直接注册到 EasyExcel 的写入链中：
 * <pre>{@code
 * EasyExcel.write(file, clazz)
 *     .registerWriteHandler(StyleTemplate.defaultStyle())
 *     .sheet("Sheet1")
 *     .doWrite(data);
 * }</pre>
 *
 * <p><b>可用样式：</b>
 * <ul>
 *   <li>{@link #defaultStyle()} —— 完整的表头+内容样式（边框、对齐、字体）</li>
 *   <li>{@link #headerOnly()} —— 仅表头样式，内容区域使用默认样式</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 */
public final class StyleTemplate {

    // ======================== 私有构造器 ========================

    /**
     * 私有构造器，防止实例化工具类。
     */
    private StyleTemplate() {}

    // ======================== 默认样式 ========================

    /**
     * 创建包含完整表头和内容格式的默认样式策略。
     *
     * <p><b>表头样式：</b>
     * <ul>
     *   <li>背景色：25%灰色（GREY_25_PERCENT）</li>
     *   <li>字体：加粗，11号字</li>
     *   <li>对齐：水平居中 + 垂直居中</li>
     *   <li>边框：四面细线边框</li>
     * </ul>
     *
     * <p><b>内容样式：</b>
     * <ul>
     *   <li>对齐：水平左对齐 + 垂直居中</li>
     *   <li>边框：四面细线边框</li>
     * </ul>
     *
     * @return 包含表头和内容样式的 {@link HorizontalCellStyleStrategy} 实例
     */
    public static HorizontalCellStyleStrategy defaultStyle() {
        // ---------- 表头样式 ----------
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setFontHeightInPoints((short) 11);
        headStyle.setWriteFont(headFont);
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headStyle.setBorderBottom(BorderStyle.THIN);
        headStyle.setBorderLeft(BorderStyle.THIN);
        headStyle.setBorderRight(BorderStyle.THIN);
        headStyle.setBorderTop(BorderStyle.THIN);

        // ---------- 内容样式 ----------
        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        contentStyle.setBorderBottom(BorderStyle.THIN);
        contentStyle.setBorderLeft(BorderStyle.THIN);
        contentStyle.setBorderRight(BorderStyle.THIN);
        contentStyle.setBorderTop(BorderStyle.THIN);

        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    // ======================== 仅表头样式 ========================

    /**
     * 创建仅包含表头格式的样式策略（内容区域不应用任何样式）。
     *
     * <p><b>表头样式：</b>
     * <ul>
     *   <li>背景色：蓝灰色（BLUE_GREY）</li>
     *   <li>字体：加粗，12号字，白色字体颜色</li>
     *   <li>对齐：水平居中 + 垂直居中</li>
     * </ul>
     *
     * <p>内容区域传入一个空的 {@link WriteCellStyle}，不会覆盖默认样式。
     * 适用于希望保留内容区域默认外观、仅突出表头的场景。
     *
     * @return 仅表头样式的 {@link HorizontalCellStyleStrategy} 实例
     */
    public static HorizontalCellStyleStrategy headerOnly() {
        // ---------- 表头样式 ----------
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headFont.setFontHeightInPoints((short) 12);
        headFont.setColor(IndexedColors.WHITE.getIndex());
        headStyle.setWriteFont(headFont);
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        // 内容区域使用默认空样式
        return new HorizontalCellStyleStrategy(headStyle, new WriteCellStyle());
    }
}
