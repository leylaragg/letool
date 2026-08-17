package com.github.leyland.letool.print.docx;

import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.HpsMeasure;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Style;

import java.math.BigInteger;

/**
 * 将宿主字体配置写入 DOCX 的正文和标题样式。
 *
 * @author leyland
 */
final class DocxStyleWriter {

    /** 六级标题使用的半磅字号。 */
    private static final int[] HEADING_SIZES = {36, 32, 28, 26, 24, 22};

    /** 写入 Normal 和 Heading 1 至 6 样式。 */
    void write(DocxRenderContext context) {
        var styles = context.wordPackage().getMainDocumentPart()
                .getStyleDefinitionsPart().getJaxbElement();
        configureStyle(context, styles.getStyle().stream()
                .filter(style -> "Normal".equals(style.getStyleId()))
                .findFirst().orElseGet(() -> addStyle(styles, "Normal", "Normal")),
                context.options().bodyFontSizeHalfPoints(), false);
        for (int level = 1; level <= 6; level++) {
            int currentLevel = level;
            String styleId = "Heading" + level;
            Style heading = styles.getStyle().stream()
                    .filter(style -> styleId.equals(style.getStyleId()))
                    .findFirst().orElseGet(() -> addStyle(
                            styles, styleId, "heading " + currentLevel));
            configureStyle(context, heading, HEADING_SIZES[level - 1], true);
        }
    }

    /** 为缺失的基础样式创建最小定义。 */
    private Style addStyle(org.docx4j.wml.Styles styles, String styleId, String name) {
        ObjectFactory factory = new ObjectFactory();
        Style style = factory.createStyle();
        style.setType("paragraph");
        style.setStyleId(styleId);
        Style.Name styleName = factory.createStyleName();
        styleName.setVal(name);
        style.setName(styleName);
        styles.getStyle().add(style);
        return style;
    }

    /** 更新样式字体、字号和标题加粗属性。 */
    private void configureStyle(
            DocxRenderContext context, Style style, int sizeHalfPoints, boolean bold) {
        ObjectFactory factory = context.factory();
        RPr properties = style.getRPr() == null ? factory.createRPr() : style.getRPr();
        RFonts fonts = factory.createRFonts();
        fonts.setAscii(context.options().westernFontFamily());
        fonts.setHAnsi(context.options().westernFontFamily());
        fonts.setEastAsia(context.options().eastAsiaFontFamily());
        properties.setRFonts(fonts);
        HpsMeasure size = factory.createHpsMeasure();
        size.setVal(BigInteger.valueOf(sizeHalfPoints));
        properties.setSz(size);
        properties.setSzCs(size);
        if (bold) {
            properties.setB(new BooleanDefaultTrue());
        }
        style.setRPr(properties);
    }
}
