package io.github.leylaragg.letool.print.xml.tag;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 按标签名保存可信自定义处理器的不可变注册表。
 *
 * @author leyland
 */
public final class PrintTagRegistry {

    /** 自定义标签名称白名单。 */
    private static final Pattern TAG_NAME = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    /** 自定义属性名称白名单。 */
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    /** 禁止覆盖的内置 DSL 标签。 */
    private static final Set<String> BUILT_IN_TAGS = Set.of(
            "document", "page", "section", "heading", "paragraph", "table", "header",
            "body", "row", "cell", "image", "bookmark", "link", "text", "field",
            "format-option", "if", "for-each", "page-break", "table-of-contents");

    /** 禁止由 XML 选择处理器实现的保留属性名称。 */
    private static final Set<String> IMPLEMENTATION_ATTRIBUTES = Set.of(
            "class", "class-name", "bean", "bean-name", "handler", "handler-class",
            "implementation", "implementation-class", "implementor", "factory", "provider",
            "provider-class");

    /** 保持注册顺序的不可变处理器索引。 */
    private final Map<String, PrintTagHandler> handlers;

    /**
     * 创建自定义标签注册表快照。
     *
     * @param handlers 待注册处理器
     */
    public PrintTagRegistry(Collection<? extends PrintTagHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers 不能为空");
        Map<String, PrintTagHandler> snapshot = new LinkedHashMap<>();
        for (PrintTagHandler handler : handlers) {
            PrintTagHandler frozen = freeze(handler);
            if (snapshot.putIfAbsent(frozen.tagName(), frozen) != null) {
                throw new IllegalArgumentException("自定义标签名称重复：" + frozen.tagName());
            }
        }
        this.handlers = Collections.unmodifiableMap(snapshot);
    }

    /** 冻结处理器的全部元数据，阻止注册后的调用方修改。 */
    private PrintTagHandler freeze(PrintTagHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("自定义标签处理器不能为 null");
        }
        String name = handler.tagName();
        if (name == null || !TAG_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("自定义标签名称不合法");
        }
        if (BUILT_IN_TAGS.contains(name)) {
            throw new IllegalArgumentException("自定义标签不能覆盖内置标签：" + name);
        }
        TagPlacement placement = Objects.requireNonNull(
                handler.placement(), "自定义标签位置不能为空");
        TagContentModel contentModel = Objects.requireNonNull(
                handler.contentModel(), "自定义标签内容模型不能为空");
        Set<String> attributes = new LinkedHashSet<>(Objects.requireNonNull(
                handler.allowedAttributes(), "自定义标签属性不能为空"));
        for (String attribute : attributes) {
            if (attribute == null || !ATTRIBUTE_NAME.matcher(attribute).matches()
                    || IMPLEMENTATION_ATTRIBUTES.contains(attribute.replace('_', '-'))) {
                throw new IllegalArgumentException("自定义标签属性名称不合法");
            }
        }
        Set<String> frozenAttributes = Collections.unmodifiableSet(attributes);
        return new FrozenTagHandler(
                name, placement, contentModel, frozenAttributes, handler);
    }

    /**
     * 查找必需自定义标签处理器。
     *
     * @param tagName 标签名
     * @return 已注册处理器
     */
    public PrintTagHandler require(String tagName) {
        PrintTagHandler handler = handlers.get(tagName);
        if (handler == null) {
            throw new IllegalArgumentException("自定义标签不存在：" + tagName);
        }
        return handler;
    }

    /**
     * 返回不可修改的标签名视图。
     *
     * @return 保持注册顺序的标签名
     */
    public Set<String> tagNames() {
        return handlers.keySet();
    }

    /** 冻结元数据并只委托编译行为的处理器快照。 */
    private static final class FrozenTagHandler implements PrintTagHandler {

        /** 已冻结标签名。 */
        private final String tagName;

        /** 已冻结放置位置。 */
        private final TagPlacement placement;

        /** 已冻结内容模型。 */
        private final TagContentModel contentModel;

        /** 已冻结属性白名单。 */
        private final Set<String> allowedAttributes;

        /** 可信处理器编译委托。 */
        private final PrintTagHandler delegate;

        /** 创建处理器元数据快照。 */
        private FrozenTagHandler(
                String tagName, TagPlacement placement, TagContentModel contentModel,
                Set<String> allowedAttributes, PrintTagHandler delegate) {
            this.tagName = tagName;
            this.placement = placement;
            this.contentModel = contentModel;
            this.allowedAttributes = allowedAttributes;
            this.delegate = delegate;
        }

        @Override
        public String tagName() {
            return tagName;
        }

        @Override
        public TagPlacement placement() {
            return placement;
        }

        @Override
        public TagContentModel contentModel() {
            return contentModel;
        }

        @Override
        public Set<String> allowedAttributes() {
            return allowedAttributes;
        }

        @Override
        public PrintTagPlan compile(TagCompileContext context) {
            return delegate.compile(context);
        }
    }
}
