package io.github.leylaragg.letool.tool.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 基于 Fastjson2 的不可变、线程安全 {@link JsonCodec} 实现。
 *
 * <p>默认实例保留 {@code JsonUtil} 原有的紧凑输出特性。需要自定义写入特性、
 * 读取特性或日期格式的应用，可以创建独立实例，而不修改 Fastjson2 全局状态。
 * 通用编解码器拒绝不安全的全局 {@code SupportAutoType} 读取特性；需要处理 Redis
 * 多态值时，应使用带独立白名单的 Redis 序列化器。</p>
 */
public final class Fastjson2JsonCodec implements JsonCodec {

    private static final JSONWriter.Feature[] LEGACY_WRITER_FEATURES = {
            JSONWriter.Feature.WriteMapNullValue,
            JSONWriter.Feature.NotWriteDefaultValue
    };

    private final JSONWriter.Feature[] writerFeatures;
    private final JSONReader.Feature[] readerFeatures;
    private final String dateFormat;

    /**
     * 根据构建器快照创建不可变编解码器。
     *
     * @param builder 已完成校验的构建配置
     */
    private Fastjson2JsonCodec(Builder builder) {
        this.writerFeatures = builder.writerFeatures.clone();
        this.readerFeatures = builder.readerFeatures.clone();
        this.dateFormat = builder.dateFormat;
    }

    /**
     * 创建保留 {@code JsonUtil} 原有紧凑输出策略的编解码器。
     *
     * @return 不可变的 Fastjson2 默认编解码器
     */
    public static Fastjson2JsonCodec createDefault() {
        return builder().build();
    }

    /**
     * 创建使用兼容性默认值初始化的构建器。
     *
     * @return 可变构建器；构建出的每个实例都持有配置的防御性副本
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 使用当前编解码器的写入特性序列化对象。
     *
     * @param value 待序列化对象，允许为 {@code null}
     * @return 紧凑 JSON；对象为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException Fastjson2 无法序列化对象时抛出
     */
    @Override
    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(value, createWriteContext(false));
        } catch (RuntimeException exception) {
            throw JsonCodecException.serializationFailed(exception);
        }
    }

    /**
     * 使用当前编解码器的写入特性和格式化输出序列化对象。
     *
     * @param value 待序列化对象，允许为 {@code null}
     * @return 格式化 JSON；对象为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException Fastjson2 无法序列化对象时抛出
     */
    @Override
    public String writePretty(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(value, createWriteContext(true));
        } catch (RuntimeException exception) {
            throw JsonCodecException.serializationFailed(exception);
        }
    }

    /**
     * 直接序列化为 UTF-8 字节数组，避免创建中间字符串。
     *
     * @param value 待序列化对象，允许为 {@code null}
     * @return UTF-8 JSON 字节数组；对象为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException Fastjson2 无法序列化对象时抛出
     */
    @Override
    public byte[] writeBytes(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONBytes(value, StandardCharsets.UTF_8, createWriteContext(false));
        } catch (RuntimeException exception) {
            throw JsonCodecException.serializationFailed(exception);
        }
    }

    /**
     * 使用当前编解码器的读取特性反序列化 JSON 文本。
     *
     * @param json JSON 文本；空白输入返回 {@code null}
     * @param targetType 目标 Java 类型，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code targetType} 为 {@code null} 时抛出
     * @throws JsonCodecException Fastjson2 无法反序列化输入时抛出
     */
    @Override
    public <T> T read(String json, Type targetType) {
        requireType(targetType, "targetType");
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(json, targetType, createReadContext());
        } catch (RuntimeException exception) {
            throw JsonCodecException.deserializationFailed(targetType, exception);
        }
    }

    /**
     * 使用当前编解码器的读取特性反序列化 JSON 数组。
     *
     * @param json JSON 数组文本；空白输入返回 {@code null}
     * @param elementType 列表元素类型，不允许为 {@code null}
     * @param <T> 列表元素类型
     * @return 反序列化后的列表；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code elementType} 为 {@code null} 时抛出
     * @throws JsonCodecException Fastjson2 无法反序列化输入时抛出
     */
    @Override
    public <T> List<T> readList(String json, Type elementType) {
        requireType(elementType, "elementType");
        if (json == null || json.isBlank()) {
            return null;
        }

        ParameterizedType listType = new SimpleParameterizedType(List.class, elementType);
        try {
            return JSON.parseObject(json, listType, createReadContext());
        } catch (RuntimeException exception) {
            throw JsonCodecException.deserializationFailed(listType, exception);
        }
    }

    /**
     * 为单次编解码操作创建隔离的写入上下文。
     *
     * @param pretty 本次操作是否启用格式化输出
     * @return 包含当前不可变配置的写入上下文
     */
    private JSONWriter.Context createWriteContext(boolean pretty) {
        JSONWriter.Context context = new JSONWriter.Context(writerFeatures);
        if (pretty) {
            context.config(JSONWriter.Feature.PrettyFormat);
        }
        if (dateFormat != null) {
            context.setDateFormat(dateFormat);
        }
        return context;
    }

    /**
     * 为单次编解码操作创建隔离的读取上下文。
     *
     * @return 包含当前不可变配置的读取上下文
     */
    private JSONReader.Context createReadContext() {
        JSONReader.Context context = new JSONReader.Context(readerFeatures);
        if (dateFormat != null) {
            context.setDateFormat(dateFormat);
        }
        return context;
    }

    /**
     * 校验调用方指定的目标类型。
     *
     * @param type 调用方传入的目标类型
     * @param argumentName 校验失败时使用的公开参数名
     * @throws IllegalArgumentException {@code type} 为 {@code null} 时抛出
     */
    private static void requireType(Type type, String argumentName) {
        if (type == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
    }

    /**
     * {@link Fastjson2JsonCodec} 不可变实例构建器。
     *
     * <p>构建器本身可变且不保证线程安全。显式传入空特性数组表示关闭该类别的全部特性。</p>
     */
    public static final class Builder {

        private JSONWriter.Feature[] writerFeatures = LEGACY_WRITER_FEATURES.clone();
        private JSONReader.Feature[] readerFeatures = new JSONReader.Feature[0];
        private String dateFormat;

        /**
         * 使用 Letool 兼容性默认值创建构建器。
         */
        private Builder() {
        }

        /**
         * 替换兼容性写入特性集合。
         *
         * @param features 不允许为 {@code null} 且不包含空元素的特性数组，允许为空数组
         * @return 当前构建器
         * @throws IllegalArgumentException 数组或任一元素为 {@code null} 时抛出
         */
        public Builder writerFeatures(JSONWriter.Feature... features) {
            this.writerFeatures = copyFeatures(features, "writerFeatures");
            return this;
        }

        /**
         * 替换读取特性集合。
         *
         * @param features 不允许为 {@code null} 且不包含空元素的特性数组，允许为空数组
         * @return 当前构建器
         * @throws IllegalArgumentException 数组或任一元素为 {@code null}，或者包含不安全的
         *                                  {@code SupportAutoType} 时抛出
         */
        @SuppressWarnings("deprecation")
        public Builder readerFeatures(JSONReader.Feature... features) {
            JSONReader.Feature[] copiedFeatures = copyFeatures(features, "readerFeatures");
            if (Arrays.asList(copiedFeatures).contains(JSONReader.Feature.SupportAutoType)) {
                throw new IllegalArgumentException(
                        "readerFeatures must not enable SupportAutoType; "
                                + "use a dedicated AutoTypeBeforeHandler allow list"
                );
            }
            this.readerFeatures = copiedFeatures;
            return this;
        }

        /**
         * 配置 Fastjson2 写入和读取共用的日期格式。
         *
         * @param dateFormat Fastjson2 日期模式或 {@code iso8601} 等特殊值；
         *                   {@code null} 表示保留 Fastjson2 默认值
         * @return 当前构建器
         * @throws IllegalArgumentException {@code dateFormat} 为空白字符串时抛出
         */
        public Builder dateFormat(String dateFormat) {
            if (dateFormat != null && dateFormat.isBlank()) {
                throw new IllegalArgumentException("dateFormat must not be blank");
            }
            this.dateFormat = dateFormat;
            return this;
        }

        /**
         * 使用配置的防御性副本构建不可变编解码器。
         *
         * @return 线程安全的编解码器实例
         */
        public Fastjson2JsonCodec build() {
            return new Fastjson2JsonCodec(this);
        }

        /**
         * 校验并复制特性数组，防止外部修改已构建实例的配置。
         *
         * @param features 调用方传入的特性数组
         * @param argumentName 校验失败时使用的公开参数名
         * @param <T> 写入或读取特性枚举类型
         * @return 校验后的数组防御性副本
         * @throws IllegalArgumentException 数组或任一元素为 {@code null} 时抛出
         */
        private static <T> T[] copyFeatures(T[] features, String argumentName) {
            if (features == null) {
                throw new IllegalArgumentException(argumentName + " must not be null");
            }
            if (Arrays.stream(features).anyMatch(feature -> feature == null)) {
                throw new IllegalArgumentException(argumentName + " must not contain null");
            }
            return features.clone();
        }
    }

    /**
     * 用于实现与提供方无关的列表反序列化的最小参数化类型。
     *
     * @param rawType 集合原始类型
     * @param actualType 指定的集合元素类型
     */
    private record SimpleParameterizedType(Type rawType, Type actualType)
            implements ParameterizedType {

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{actualType};
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public String getTypeName() {
            return rawType.getTypeName() + "<" + actualType.getTypeName() + ">";
        }
    }
}
