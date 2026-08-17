package io.github.leylaragg.letool.print.xml.tag;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 自定义标签处理器使用的不可变编译上下文。
 *
 * @author leyland
 */
public final class TagCompileContext {

    /** 已注册标签名。 */
    private final String tagName;

    /** 经过白名单校验的静态属性。 */
    private final Map<String, String> attributes;

    /** 不包含模板正文的安全位置说明。 */
    private final String location;

    /**
     * 创建标签编译上下文。
     *
     * @param tagName 标签名
     * @param attributes 白名单属性
     * @param location 安全位置说明
     */
    public TagCompileContext(String tagName, Map<String, String> attributes, String location) {
        this.tagName = Objects.requireNonNull(tagName, "tagName 不能为空");
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes 不能为空"));
        this.location = Objects.requireNonNull(location, "location 不能为空");
    }

    /** @return 标签名 */
    public String tagName() {
        return tagName;
    }

    /** @return 不可修改的白名单属性 */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * 查找可选属性。
     *
     * @param name 属性名
     * @return 属性值，不存在时为空
     */
    public Optional<String> attribute(String name) {
        return Optional.ofNullable(attributes.get(name));
    }

    /** @return 安全位置说明 */
    public String location() {
        return location;
    }
}
