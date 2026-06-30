package com.github.leyland.letool.tool.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具类——Fastjson2 统一封装.
 *
 * <p>基于 {@code com.alibaba.fastjson2}，全局统一序列化策略：</p>
 * <ul>
 *   <li>输出 {@code null} 值字段（避免反序列化信息丢失）</li>
 *   <li>跳过默认值（减小 JSON 体积）</li>
 *   <li>日期格式 {@code yyyy-MM-dd HH:mm:ss}</li>
 * </ul>
 *
 * <p>所有方法均为空安全：传入 {@code null} 或空白字符串返回 {@code null}.</p>
 */
public final class JsonUtil {

    private JsonUtil() {}

    /** 全局写入 Feature：写 null 值、跳过默认值 */
    private static final JSONWriter.Feature[] WRITER_FEATURES = {
            JSONWriter.Feature.WriteMapNullValue,
            JSONWriter.Feature.NotWriteDefaultValue
    };

    // ======================== 序列化 ========================

    /**
     * 对象序列化为 JSON 字符串.
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，{@code obj} 为 {@code null} 返回 {@code null}
     */
    public static String toJsonString(Object obj) {
        if (obj == null) return null;
        return JSON.toJSONString(obj, WRITER_FEATURES);
    }

    /**
     * 对象序列化为格式化（美化）JSON 字符串，适合日志输出.
     *
     * @param obj 待序列化对象
     * @return 带缩进和换行的 JSON 字符串
     */
    public static String toPrettyJson(Object obj) {
        if (obj == null) return null;
        return JSON.toJSONString(obj, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 对象序列化为字节数组.
     *
     * @param obj 待序列化对象
     * @return JSON 字节数组
     */
    public static byte[] toJsonBytes(Object obj) {
        if (obj == null) return null;
        return JSON.toJSONBytes(obj, WRITER_FEATURES);
    }

    // ======================== 反序列化 ========================

    /**
     * JSON 字符串反序列化为指定类型.
     *
     * @param json  JSON 字符串，空白返回 {@code null}
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 反序列化后的对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (StrUtil.isBlank(json)) return null;
        return JSON.parseObject(json, clazz);
    }

    /**
     * JSON 字节数组反序列化为指定类型.
     */
    public static <T> T parseObject(byte[] json, Class<T> clazz) {
        if (json == null || json.length == 0) return null;
        return JSON.parseObject(json, clazz);
    }

    /**
     * JSON 字符串反序列化为泛型类型，用于嵌套泛型场景.
     *
     * <pre>{@code
     * // 反序列化 List<User>
     * List<User> users = JsonUtil.parseObject(json,
     *     new TypeReference<List<User>>() {}.getType());
     * }</pre>
     *
     * @param json JSON 字符串
     * @param type 目标泛型类型（通过 TypeReference 获取）
     * @return 反序列化后的对象
     */
    public static <T> T parseObject(String json, Type type) {
        if (StrUtil.isBlank(json)) return null;
        return JSON.parseObject(json, type);
    }

    /**
     * JSON 数组字符串反序列化为 List.
     *
     * @param json  JSON 数组字符串
     * @param clazz 元素类型
     * @param <T>   元素泛型
     * @return 列表，空白返回 {@code null}
     */
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        if (StrUtil.isBlank(json)) return null;
        return JSON.parseArray(json, clazz);
    }

    // ======================== JSONObject / JSONArray ========================

    /**
     * 解析为 {@link JSONObject}，适合未知结构的 JSON 对象.
     */
    public static JSONObject parseObject(String json) {
        if (StrUtil.isBlank(json)) return null;
        return JSON.parseObject(json);
    }

    /**
     * 解析为 {@link JSONArray}，适合未知结构的 JSON 数组.
     */
    public static JSONArray parseArray(String json) {
        if (StrUtil.isBlank(json)) return null;
        return JSON.parseArray(json);
    }

    // ======================== 对象转换 ========================

    /**
     * 对象转 Map.
     *
     * @param obj 任意 JavaBean
     * @return 字段名 → 值的映射
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        return JSON.parseObject(JSON.toJSONString(obj), Map.class);
    }

    /**
     * Map 转 JavaBean.
     *
     * @param map   key 为字段名，value 为字段值的 Map
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 转换后的 Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T toBean(Map<String, Object> map, Class<T> clazz) {
        if (map == null) return null;
        return (T) JSON.to(clazz, map);
    }

    /**
     * 对象类型转换（先序列化再反序列化，性能较低，少量使用）.
     *
     * @param obj         源对象
     * @param targetClass 目标类型
     * @param <T>         目标泛型
     * @return 转换后的对象
     */
    public static <T> T convert(Object obj, Class<T> targetClass) {
        if (obj == null) return null;
        String json = toJsonString(obj);
        return parseObject(json, targetClass);
    }
}
