package io.github.leylaragg.letool.tool.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import io.github.leylaragg.letool.tool.json.JsonCodecException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * JSON 静态兼容门面。
 *
 * <p>原有方法使用不可变的 Fastjson2 编解码器，并保留历史紧凑输出策略。每项操作
 * 同时提供接收 {@link JsonCodec} 的重载，使独立调用方可以选择其他实现，且不修改
 * 全局状态。Spring 应用通常应直接注入自动配置的 {@code JsonCodec} Bean。</p>
 *
 * <p>对象参数支持 {@code null}，JSON 文本中的空白内容按 {@code null} 处理。
 * 无效目标类型通过 {@link IllegalArgumentException} 快速失败；JSON 实现异常统一
 * 使用 {@link JsonCodecException}。</p>
 */
public final class JsonUtil {

    /** 原有静态入口使用的不可变兼容编解码器。 */
    private static final JsonCodec DEFAULT_CODEC = Fastjson2JsonCodec.createDefault();

    /**
     * 不启用写入特性的编解码器，用于兼容历史上绕过紧凑输出特性数组的方法。
     */
    private static final JsonCodec LEGACY_PLAIN_CODEC = Fastjson2JsonCodec.builder()
            .writerFeatures()
            .build();

    /**
     * 禁止实例化静态工具门面。
     */
    private JsonUtil() {
    }

    // ======================== 序列化 ========================

    /**
     * 使用兼容编解码器序列化对象。
     *
     * @param obj 待序列化对象，允许为 {@code null}
     * @return 紧凑 JSON；对象为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException 序列化失败时抛出
     */
    public static String toJsonString(Object obj) {
        return toJsonString(obj, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器序列化对象。
     *
     * @param obj 待序列化对象，允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @return 紧凑 JSON；对象为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 序列化失败时抛出
     */
    public static String toJsonString(Object obj, JsonCodec codec) {
        JsonCodec requiredCodec = requireCodec(codec);
        return obj == null ? null : requiredCodec.write(obj);
    }

    /**
     * 使用兼容编解码器将对象序列化为格式化 JSON。
     *
     * @param obj 待序列化对象，允许为 {@code null}
     * @return 格式化 JSON；对象为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException 序列化失败时抛出
     */
    public static String toPrettyJson(Object obj) {
        return toPrettyJson(obj, LEGACY_PLAIN_CODEC);
    }

    /**
     * 使用显式指定的编解码器将对象序列化为格式化 JSON。
     *
     * @param obj 待序列化对象，允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @return 格式化 JSON；对象为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 序列化失败时抛出
     */
    public static String toPrettyJson(Object obj, JsonCodec codec) {
        JsonCodec requiredCodec = requireCodec(codec);
        return obj == null ? null : requiredCodec.writePretty(obj);
    }

    /**
     * 使用兼容编解码器将对象序列化为 UTF-8 JSON 字节数组。
     *
     * @param obj 待序列化对象，允许为 {@code null}
     * @return UTF-8 JSON 字节数组；对象为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException 序列化失败时抛出
     */
    public static byte[] toJsonBytes(Object obj) {
        return toJsonBytes(obj, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器将对象序列化为 UTF-8 JSON 字节数组。
     *
     * @param obj 待序列化对象，允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @return UTF-8 JSON 字节数组；对象为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 序列化失败时抛出
     */
    public static byte[] toJsonBytes(Object obj, JsonCodec codec) {
        JsonCodec requiredCodec = requireCodec(codec);
        return obj == null ? null : requiredCodec.writeBytes(obj);
    }

    // ======================== 反序列化 ========================

    /**
     * 使用兼容编解码器将 JSON 文本反序列化为指定类。
     *
     * @param json JSON 文本；空白输入返回 {@code null}
     * @param clazz 目标类，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        return parseObject(json, clazz, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器将 JSON 文本反序列化为指定类。
     *
     * @param json JSON 文本；空白输入返回 {@code null}
     * @param clazz 目标类，不允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 或 {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> T parseObject(String json, Class<T> clazz, JsonCodec codec) {
        return requireCodec(codec).read(json, requireType(clazz, "clazz"));
    }

    /**
     * 使用兼容编解码器将 UTF-8 JSON 字节数组反序列化为指定类。
     *
     * @param json UTF-8 JSON 字节数组；空输入返回 {@code null}
     * @param clazz 目标类，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；空输入返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> T parseObject(byte[] json, Class<T> clazz) {
        return parseObject(json, clazz, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器将 UTF-8 JSON 字节数组反序列化为指定类。
     *
     * @param json UTF-8 JSON 字节数组；空输入返回 {@code null}
     * @param clazz 目标类，不允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；空输入返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 或 {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> T parseObject(byte[] json, Class<T> clazz, JsonCodec codec) {
        return requireCodec(codec).read(json, requireType(clazz, "clazz"));
    }

    /**
     * 将 JSON 文本反序列化为指定类或参数化类型。
     *
     * @param json JSON 文本；空白输入返回 {@code null}
     * @param type 目标类型，通常由类型引用获取，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code type} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> T parseObject(String json, Type type) {
        return parseObject(json, type, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器将 JSON 文本反序列化为指定类或参数化类型。
     *
     * @param json JSON 文本；空白输入返回 {@code null}
     * @param type 目标类型，通常由类型引用获取，不允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 反序列化结果；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code type} 或 {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> T parseObject(String json, Type type, JsonCodec codec) {
        return requireCodec(codec).read(json, requireType(type, "type"));
    }

    /**
     * 使用兼容编解码器反序列化 JSON 数组。
     *
     * @param json JSON 数组文本；空白输入返回 {@code null}
     * @param clazz 元素类，不允许为 {@code null}
     * @param <T> 元素类型
     * @return 反序列化后的列表；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        return parseArray(json, clazz, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器反序列化 JSON 数组。
     *
     * @param json JSON 数组文本；空白输入返回 {@code null}
     * @param clazz 元素类，不允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @param <T> 元素类型
     * @return 反序列化后的列表；空白输入返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 或 {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz, JsonCodec codec) {
        return requireCodec(codec).readList(json, requireType(clazz, "clazz"));
    }

    // ======================== Fastjson2 兼容节点 ========================

    /**
     * 将 JSON 文本解析为 Fastjson2 对象节点。
     *
     * <p>该兼容方法有意返回底层实现专有类型。调用方需要与 Fastjson2 解耦时，
     * 应使用 {@link #parseObject(String, Class, JsonCodec)} 并指定 {@link Map}。</p>
     *
     * @param json JSON 对象文本；空白输入返回 {@code null}
     * @return Fastjson2 对象节点；空白输入返回 {@code null}
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static JSONObject parseObject(String json) {
        return DEFAULT_CODEC.read(json, JSONObject.class);
    }

    /**
     * 将 JSON 文本解析为 Fastjson2 数组节点。
     *
     * <p>该兼容方法有意返回底层实现专有类型。需要与底层实现解耦时，应显式指定
     * 编解码器和 Java 集合类型。</p>
     *
     * @param json JSON 数组文本；空白输入返回 {@code null}
     * @return Fastjson2 数组节点；空白输入返回 {@code null}
     * @throws JsonCodecException 反序列化失败时抛出
     */
    public static JSONArray parseArray(String json) {
        return DEFAULT_CODEC.read(json, JSONArray.class);
    }

    // ======================== 对象转换 ========================

    /**
     * 使用兼容编解码器将 JavaBean 转换为 Map。
     *
     * @param obj 源对象，允许为 {@code null}
     * @return 字段名到字段值的映射；源对象为 {@code null} 时返回 {@code null}
     * @throws JsonCodecException 转换失败时抛出
     */
    public static Map<String, Object> toMap(Object obj) {
        return toMap(obj, LEGACY_PLAIN_CODEC);
    }

    /**
     * 使用显式指定的编解码器将 JavaBean 转换为 Map。
     *
     * @param obj 源对象，允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @return 字段名到字段值的映射；源对象为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 转换失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object obj, JsonCodec codec) {
        JsonCodec requiredCodec = requireCodec(codec);
        if (obj == null) {
            return null;
        }
        return requiredCodec.read(requiredCodec.write(obj), Map.class);
    }

    /**
     * 使用兼容编解码器将 Map 转换为 JavaBean。
     *
     * @param map 源 Map，允许为 {@code null}
     * @param clazz 目标类，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 转换后的 JavaBean；源 Map 为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 为 {@code null} 时抛出
     * @throws JsonCodecException 转换失败时抛出
     */
    public static <T> T toBean(Map<String, Object> map, Class<T> clazz) {
        return toBean(map, clazz, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器将 Map 转换为 JavaBean。
     *
     * @param map 源 Map，允许为 {@code null}
     * @param clazz 目标类，不允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 转换后的 JavaBean；源 Map 为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code clazz} 或 {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 转换失败时抛出
     */
    public static <T> T toBean(Map<String, Object> map, Class<T> clazz, JsonCodec codec) {
        JsonCodec requiredCodec = requireCodec(codec);
        Class<T> requiredClass = requireType(clazz, "clazz");
        return map == null ? null : requiredCodec.read(requiredCodec.write(map), requiredClass);
    }

    /**
     * 使用兼容编解码器将对象转换为另一种 Java 类型。
     *
     * <p>转换过程使用 JSON 往返，适合低频边界适配，不适合性能敏感的热点循环。</p>
     *
     * @param obj 源对象，允许为 {@code null}
     * @param targetClass 目标类，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 转换后的对象；源对象为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code targetClass} 为 {@code null} 时抛出
     * @throws JsonCodecException 转换失败时抛出
     */
    public static <T> T convert(Object obj, Class<T> targetClass) {
        return convert(obj, targetClass, DEFAULT_CODEC);
    }

    /**
     * 使用显式指定的编解码器将对象转换为另一种 Java 类型。
     *
     * @param obj 源对象，允许为 {@code null}
     * @param targetClass 目标类，不允许为 {@code null}
     * @param codec 仅用于本次调用的编解码器，不允许为 {@code null}
     * @param <T> 返回值类型
     * @return 转换后的对象；源对象为 {@code null} 时返回 {@code null}
     * @throws IllegalArgumentException {@code targetClass} 或 {@code codec} 为 {@code null} 时抛出
     * @throws JsonCodecException 转换失败时抛出
     */
    public static <T> T convert(Object obj, Class<T> targetClass, JsonCodec codec) {
        JsonCodec requiredCodec = requireCodec(codec);
        Class<T> requiredClass = requireType(targetClass, "targetClass");
        return obj == null ? null : requiredCodec.read(requiredCodec.write(obj), requiredClass);
    }

    /**
     * 校验显式指定的编解码器。
     *
     * @param codec 调用方传入的编解码器
     * @return 校验后的编解码器
     * @throws IllegalArgumentException {@code codec} 为 {@code null} 时抛出
     */
    private static JsonCodec requireCodec(JsonCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        return codec;
    }

    /**
     * 在保留静态类型的同时校验类或泛型目标类型。
     *
     * @param type 调用方传入的目标类型
     * @param argumentName 校验失败时使用的公开参数名
     * @param <T> 具体的 {@link Type} 子类型
     * @return 校验后的目标类型
     * @throws IllegalArgumentException {@code type} 为 {@code null} 时抛出
     */
    private static <T extends Type> T requireType(T type, String argumentName) {
        if (type == null) {
            throw new IllegalArgumentException(argumentName + " must not be null");
        }
        return type;
    }
}
