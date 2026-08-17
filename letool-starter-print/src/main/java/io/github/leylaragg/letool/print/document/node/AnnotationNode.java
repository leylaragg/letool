package io.github.leylaragg.letool.print.document.node;

import io.github.leylaragg.letool.print.exception.PrintValidationException;

import java.util.Objects;

/**
 * 依附于目标节点首个可见区域的纯文本批注。
 *
 * <p>尺寸和偏移使用微米，输出实现负责把稳定物理单位转换为自身坐标。</p>
 *
 * @author leyland
 */
public final class AnnotationNode implements BlockNode {

    /** 单条批注允许保存的最大字符数。 */
    private static final int MAX_CONTENT_CHARACTERS = 50_000;

    /** 批注作者允许保存的最大字符数。 */
    private static final int MAX_AUTHOR_CHARACTERS = 128;

    /** 单边最大尺寸为 500 mm。 */
    private static final int MAX_SIZE_MICROMETERS = 500_000;

    /** 单轴最大偏移为 2,000 mm。 */
    private static final int MAX_OFFSET_MICROMETERS = 2_000_000;

    /** 批注呈现类型。 */
    private final AnnotationType type;

    /** 被批注节点的逻辑 ID。 */
    private final String targetId;

    /** 相对目标可见区域的锚点。 */
    private final AnnotationPlacement placement;

    /** 批注矩形宽度。 */
    private final int widthMicrometers;

    /** 批注矩形高度。 */
    private final int heightMicrometers;

    /** 相对锚点的水平偏移。 */
    private final int offsetXMicrometers;

    /** 相对锚点的垂直偏移。 */
    private final int offsetYMicrometers;

    /** 可选批注作者。 */
    private final String author;

    /** 不含脚本或资源语义的批注正文。 */
    private final String content;

    /**
     * 创建不可变批注。
     *
     * @param type 批注类型
     * @param targetId 目标节点逻辑 ID
     * @param placement 相对目标的锚点
     * @param widthMicrometers 批注宽度，范围为 1 至 500,000 微米
     * @param heightMicrometers 批注高度，范围为 1 至 500,000 微米
     * @param offsetXMicrometers 水平偏移，绝对值不超过 2,000,000 微米
     * @param offsetYMicrometers 垂直偏移，绝对值不超过 2,000,000 微米
     * @param author 可选作者，{@code null} 按空作者处理
     * @param content 非空白纯文本正文
     */
    public AnnotationNode(
            AnnotationType type,
            String targetId,
            AnnotationPlacement placement,
            int widthMicrometers,
            int heightMicrometers,
            int offsetXMicrometers,
            int offsetYMicrometers,
            String author,
            String content) {
        this.type = Objects.requireNonNull(type, "type 不能为空");
        this.targetId = NodeValidation.requiredId(targetId);
        this.placement = Objects.requireNonNull(placement, "placement 不能为空");
        this.widthMicrometers = validateSize(widthMicrometers, "批注宽度");
        this.heightMicrometers = validateSize(heightMicrometers, "批注高度");
        this.offsetXMicrometers = validateOffset(offsetXMicrometers, "批注水平偏移");
        this.offsetYMicrometers = validateOffset(offsetYMicrometers, "批注垂直偏移");
        this.author = normalizeAuthor(author);
        if (content == null || content.isBlank()) {
            throw PrintValidationException.invalidDocument("批注正文不能为空");
        }
        if (content.length() > MAX_CONTENT_CHARACTERS) {
            throw PrintValidationException.invalidDocument(
                    "批注正文不能超过 " + MAX_CONTENT_CHARACTERS + " 个字符");
        }
        this.content = content;
    }

    /** 批注本身不参与目标定位。 */
    @Override
    public String id() {
        return "";
    }

    /** @return 批注类型 */
    public AnnotationType type() {
        return type;
    }

    /** @return 目标节点逻辑 ID */
    public String targetId() {
        return targetId;
    }

    /** @return 相对目标的锚点 */
    public AnnotationPlacement placement() {
        return placement;
    }

    /** @return 批注宽度，单位为微米 */
    public int widthMicrometers() {
        return widthMicrometers;
    }

    /** @return 批注高度，单位为微米 */
    public int heightMicrometers() {
        return heightMicrometers;
    }

    /** @return 水平偏移，单位为微米 */
    public int offsetXMicrometers() {
        return offsetXMicrometers;
    }

    /** @return 垂直偏移，单位为微米 */
    public int offsetYMicrometers() {
        return offsetYMicrometers;
    }

    /** @return 批注作者；没有声明时返回空字符串 */
    public String author() {
        return author;
    }

    /** @return 批注纯文本正文 */
    public String content() {
        return content;
    }

    /** 校验单边尺寸没有超过通用物理边界。 */
    private static int validateSize(int value, String name) {
        if (value < 1 || value > MAX_SIZE_MICROMETERS) {
            throw PrintValidationException.invalidDocument(
                    name + "必须在 1 到 " + MAX_SIZE_MICROMETERS + " 微米之间");
        }
        return value;
    }

    /** 校验带方向的偏移仍在可治理范围内。 */
    private static int validateOffset(int value, String name) {
        if (Math.abs((long) value) > MAX_OFFSET_MICROMETERS) {
            throw PrintValidationException.invalidDocument(
                    name + "绝对值不能超过 " + MAX_OFFSET_MICROMETERS + " 微米");
        }
        return value;
    }

    /** 规范化可选作者，避免只包含空白的元数据进入产物。 */
    private static String normalizeAuthor(String author) {
        if (author == null) {
            return "";
        }
        String normalized = author.trim();
        if (normalized.length() > MAX_AUTHOR_CHARACTERS) {
            throw PrintValidationException.invalidDocument(
                    "批注作者不能超过 " + MAX_AUTHOR_CHARACTERS + " 个字符");
        }
        return normalized;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AnnotationNode that)) {
            return false;
        }
        return widthMicrometers == that.widthMicrometers
                && heightMicrometers == that.heightMicrometers
                && offsetXMicrometers == that.offsetXMicrometers
                && offsetYMicrometers == that.offsetYMicrometers
                && type == that.type
                && targetId.equals(that.targetId)
                && placement == that.placement
                && author.equals(that.author)
                && content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, targetId, placement, widthMicrometers,
                heightMicrometers, offsetXMicrometers, offsetYMicrometers, author, content);
    }

    @Override
    public String toString() {
        return "AnnotationNode[type=" + type + ", targetId=" + targetId
                + ", placement=" + placement + ", widthMicrometers=" + widthMicrometers
                + ", heightMicrometers=" + heightMicrometers
                + ", offsetXMicrometers=" + offsetXMicrometers
                + ", offsetYMicrometers=" + offsetYMicrometers
                + ", author=" + author + ", content=" + content + "]";
    }
}
