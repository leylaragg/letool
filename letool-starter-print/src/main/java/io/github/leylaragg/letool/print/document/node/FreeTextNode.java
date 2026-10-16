package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.document.style.DocumentColor;
import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 参与正常排版的可编辑纯文本框，宽度由父容器决定，高度始终容纳完整内容。
 *
 * @param id 可选逻辑定位 ID；重复区域使用空 ID
 * @param textStyleName 文字样式，控制字体、字号、行高和颜色
 * @param boxStyleName 单元格样式，控制边框、填充和内边距
 * @param author 可选作者
 * @param content 非空白纯文本
 * @param widthMode 填满可用宽度或按完整文字宽度收窄
 * @param background 可选的单框填充色；null 时继承命名样式
 * @author leyland
 */
public record FreeTextNode(String id, String textStyleName, String boxStyleName, String author,
        String content, WidthMode widthMode, DocumentColor background) implements BlockNode, InlineNode {

    /** 与普通批注共用文本容量边界。 */
    private static final int MAX_CONTENT_CHARACTERS = 50_000;
    private static final int MAX_AUTHOR_CHARACTERS = 128;

    /** 文本框在原生文档流中的宽度策略。 */
    public enum WidthMode {
        /** 使用父容器的全部可用宽度。 */
        FILL,
        /** 以文字宽度收窄，超出父容器时在框内完整换行。 */
        FIT_CONTENT
    }

    /** 创建使用命名填充色和可用宽度的块级文本框。 */
    public FreeTextNode(String id, String textStyleName, String boxStyleName, String author, String content) {
        this(id, textStyleName, boxStyleName, author, content, WidthMode.FILL, null);
    }

    /** 冻结可供块级和行内排版共用的文本及样式引用。 */
    public FreeTextNode {
        id = NodeValidation.optionalId(id);
        textStyleName = textStyleName == null ? "" : textStyleName;
        boxStyleName = boxStyleName == null ? "" : boxStyleName;
        author = author == null ? "" : author.trim();
        Objects.requireNonNull(widthMode, "widthMode 不能为空");
        if (author.length() > MAX_AUTHOR_CHARACTERS) {
            throw PrintValidationException.invalidDocument("文本框作者不能超过 " + MAX_AUTHOR_CHARACTERS + " 个字符");
        }
        if (content == null || content.isBlank()) {
            throw PrintValidationException.invalidDocument("文本框正文不能为空");
        }
        if (content.length() > MAX_CONTENT_CHARACTERS) {
            throw PrintValidationException.invalidDocument("文本框正文不能超过 " + MAX_CONTENT_CHARACTERS + " 个字符");
        }
    }
}
