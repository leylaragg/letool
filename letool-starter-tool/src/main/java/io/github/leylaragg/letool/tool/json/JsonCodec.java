package io.github.leylaragg.letool.tool.json;

import io.github.leylaragg.letool.tool.annotation.SPI;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JSON 序列化与反序列化扩展接口。
 *
 * <p>该接口只暴露 Java 标准的 {@link Type}，不向调用方泄露 Fastjson2 专有类型。
 * 应用可以将 Starter 默认实现替换为 Jackson、Gson 或其他实现，而无须修改依赖
 * 本接口的业务代码。</p>
 *
 * <p>实现类在构造完成后必须保证线程安全。{@code null} 对象序列化为 {@code null}；
 * {@code null}、空数组或空白 JSON 输入均反序列化为 {@code null}。编解码技术异常
 * 应统一转换为 {@link JsonCodecException}，并保留原始异常原因。</p>
 */
@SPI
public interface JsonCodec {

    /**
     * 将对象序列化为紧凑 JSON。
     *
     * @param value 待序列化对象，允许为 {@code null}
     * @return 紧凑 JSON；当 {@code value} 为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException 对象无法序列化时抛出
     */
    String write(Object value);

    /**
     * 将对象序列化为便于阅读的格式化 JSON。
     *
     * @param value 待序列化对象，允许为 {@code null}
     * @return 格式化 JSON；当 {@code value} 为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException 对象无法序列化时抛出
     */
    String writePretty(Object value);

    /**
     * 将对象序列化为 UTF-8 JSON 字节数组。
     *
     * <p>默认实现复用 {@link #write(Object)}。实现类可以覆盖该方法，以避免创建
     * 中间字符串。</p>
     *
     * @param value 待序列化对象，允许为 {@code null}
     * @return UTF-8 JSON 字节数组；当 {@code value} 为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException 对象无法序列化时抛出
     */
    default byte[] writeBytes(Object value) {
        String json = write(value);
        return json == null ? null : json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将 JSON 文本反序列化为指定 Java 类型。
     *
     * @param json JSON 文本；为 {@code null}、空字符串或空白字符串时返回 {@code null}
     * @param targetType 目标类型，支持参数化类型，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；输入为空时返回 {@code null}
     * @throws IllegalArgumentException {@code targetType} 为 {@code null} 时抛出
     * @throws JsonCodecException JSON 无法反序列化时抛出
     */
    <T> T read(String json, Type targetType);

    /**
     * 将 UTF-8 JSON 字节数组反序列化为指定 Java 类型。
     *
     * <p>默认实现复用 {@link #read(String, Type)}。实现类可以覆盖该方法，直接从
     * 字节数组完成反序列化。</p>
     *
     * @param json UTF-8 JSON 字节数组；为 {@code null} 或空数组时返回 {@code null}
     * @param targetType 目标类型，支持参数化类型，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；输入为空时返回 {@code null}
     * @throws IllegalArgumentException {@code targetType} 为 {@code null} 时抛出
     * @throws JsonCodecException JSON 无法反序列化时抛出
     */
    default <T> T read(byte[] json, Type targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        if (json == null || json.length == 0) {
            return null;
        }
        return read(new String(json, StandardCharsets.UTF_8), targetType);
    }

    /**
     * 按指定元素类型反序列化 JSON 数组。
     *
     * @param json JSON 数组文本；为 {@code null}、空字符串或空白字符串时返回 {@code null}
     * @param elementType 列表元素类型，不允许为 {@code null}
     * @param <T> 列表元素类型
     * @return 反序列化后的列表；输入为空时返回 {@code null}
     * @throws IllegalArgumentException {@code elementType} 为 {@code null} 时抛出
     * @throws JsonCodecException JSON 无法反序列化时抛出
     */
    <T> List<T> readList(String json, Type elementType);
}
