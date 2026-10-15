package io.github.leylaragg.letool.print.pdf;

import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.extend.FSObjectDrawer;
import com.openhtmltopdf.extend.FSObjectDrawerFactory;
import com.openhtmltopdf.pdfboxout.PdfBoxOutputDevice;
import org.w3c.dom.Element;

import java.awt.BasicStroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * 只绘制通用节点已经校验的几何参数。
 *
 * @author leyland
 * @date 2026/10/14
 */
final class PdfShapeDrawerFactory implements FSObjectDrawerFactory {
    /**
     * 判断元素是否由受控向量节点生成。
     *
     * @param element 当前 XHTML 元素
     * @return 是否使用本工厂的原生绘制器
     */
    @Override
    public boolean isReplacedObject(Element element) {
        return "letool-shape".equals(element.getAttribute("type"));
    }

    /**
     * 创建按模板点尺寸绘制的无状态向量绘制器。
     *
     * @param element 已通过节点校验的受控向量元素
     * @return 将点尺寸换算为渲染器坐标的绘制器
     */
    @Override
    public FSObjectDrawer createDrawer(Element element) {
        return (source, x, y, width, height, device, context, dotsPerPixel) -> {
            // 节点尺寸以 pt 声明，绘制设备使用 dots；布局传入的外框已经完成换算。
            double dots = ((PdfBoxOutputDevice) device).getDotsPerPoint();
            double stroke = Double.parseDouble(source.getAttribute("data-stroke")) * dots;
            double mark = Double.parseDouble(source.getAttribute("data-mark")) * dots;
            boolean selected = Boolean.parseBoolean(source.getAttribute("data-selected"));
            device.setColor(new FSRGBColor(0, 0, 0));
            device.setStroke(new BasicStroke((float) stroke));

            switch (source.getAttribute("data-kind")) {
                case "CIRCLE" -> {
                    device.draw(new Ellipse2D.Double(x, y, width, height));
                    if (selected) {
                        device.fill(new Ellipse2D.Double(x + (width - mark) / 2, y + (height - mark) / 2, mark, mark));
                    }
                }
                case "SQUARE" -> {
                    device.draw(new Rectangle2D.Double(x, y, width, height));
                    if (selected) {
                        Path2D cross = new Path2D.Double();
                        double left = x + (width - mark) / 2;
                        double top = y + (height - mark) / 2;
                        cross.moveTo(left, top);
                        cross.lineTo(left + mark, top + mark);
                        cross.moveTo(left + mark, top);
                        cross.lineTo(left, top + mark);
                        device.draw(cross);
                    }
                }
                case "U_CELLS" -> {
                    int cells = Integer.parseInt(source.getAttribute("data-cells"));
                    // U 格共用底边与竖边，内部边界只绘制一次。
                    Path2D path = new Path2D.Double();
                    path.moveTo(x, y + height);
                    path.lineTo(x + width, y + height);
                    for (int index = 0; index <= cells; index++) {
                        double at = x + width * index / cells;
                        path.moveTo(at, y);
                        path.lineTo(at, y + height);
                    }
                    device.draw(path);
                }
                case "UNDERLINE" -> {
                    Path2D line = new Path2D.Double();
                    line.moveTo(x, y + height - mark);
                    line.lineTo(x + width, y + height - mark);
                    device.draw(line);
                }
                case "SPACE" -> {
                    // 空白形状仅参与布局，不向内容流写入图形。
                }
                default -> throw new IllegalArgumentException("未知受控向量类型");
            }
            return null;
        };
    }
}
