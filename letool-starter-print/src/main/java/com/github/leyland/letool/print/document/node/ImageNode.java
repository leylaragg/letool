package com.github.leyland.letool.print.document.node;

import com.github.leyland.letool.print.exception.PrintValidationException;

/**
 * 引用受控逻辑资源的图片节点。
 *
 * @author leyland
 */
public final class ImageNode implements BlockNode {

    /** 图片逻辑 ID。 */
    private final String id;

    /** 受控逻辑资源 ID。 */
    private final String resourceId;

    /** 图片替代文本。 */
    private final String altText;

    /** 图片宽度。 */
    private final int widthMicrometers;

    /** 图片高度。 */
    private final int heightMicrometers;

    /**
     * 创建图片节点。
     *
     * @param id 图片逻辑 ID
     * @param resourceId 受控逻辑资源 ID
     * @param altText 替代文本
     * @param widthMicrometers 图片宽度
     * @param heightMicrometers 图片高度
     */
    public ImageNode(String id, String resourceId, String altText,
                     int widthMicrometers, int heightMicrometers) {
        this.id = NodeValidation.optionalId(id);
        if (resourceId == null || resourceId.isBlank()) {
            throw PrintValidationException.invalidDocument("图片资源 ID 不能为空");
        }
        if (altText == null) {
            throw PrintValidationException.invalidDocument("图片替代文本不能为 null");
        }
        if (widthMicrometers < 1 || widthMicrometers > 2_000_000
                || heightMicrometers < 1 || heightMicrometers > 2_000_000) {
            throw PrintValidationException.invalidDocument("图片边长必须在 1 微米到 2 米之间");
        }
        this.resourceId = resourceId;
        this.altText = altText;
        this.widthMicrometers = widthMicrometers;
        this.heightMicrometers = heightMicrometers;
    }

    /** @return 图片逻辑 ID */
    @Override
    public String id() {
        return id;
    }

    /** @return 受控逻辑资源 ID */
    public String resourceId() {
        return resourceId;
    }

    /** @return 图片替代文本 */
    public String altText() {
        return altText;
    }

    /** @return 图片宽度 */
    public int widthMicrometers() {
        return widthMicrometers;
    }

    /** @return 图片高度 */
    public int heightMicrometers() {
        return heightMicrometers;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ImageNode that)) {
            return false;
        }
        return widthMicrometers == that.widthMicrometers
                && heightMicrometers == that.heightMicrometers
                && id.equals(that.id)
                && resourceId.equals(that.resourceId)
                && altText.equals(that.altText);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + resourceId.hashCode();
        result = 31 * result + altText.hashCode();
        result = 31 * result + Integer.hashCode(widthMicrometers);
        result = 31 * result + Integer.hashCode(heightMicrometers);
        return result;
    }

    @Override
    public String toString() {
        return "ImageNode[id=" + id + ", resourceId=" + resourceId
                + ", altText=" + altText + ", widthMicrometers=" + widthMicrometers
                + ", heightMicrometers=" + heightMicrometers + "]";
    }
}
