package com.github.leyland.data.mapper;

import java.util.Map;

/**
 * Map字段值提供者
 * 用于支持从Map中获取值作为数据源
 *
 * @author leyland
 * @date 2025-01-08
 */
public class MapFieldValueProvider {

    /**
     * 从Map中获取值
     * 支持嵌套Map访问，例如：user.address.city
     *
     * @param map 数据Map
     * @param key 键，支持点号分隔的嵌套路径
     * @return 值
     */
    public static Object getValue(Map<?, ?> map, String key) {
        if (map == null || key == null || key.isEmpty()) {
            return null;
        }

        if (!key.contains(".")) {
            return map.get(key);
        }

        // 处理嵌套路径
        String[] parts = key.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                // 尝试通过反射获取嵌套对象属性
                current = ObjectMapper.getFieldValue(current, part);
            }

            if (current == null) {
                return null;
            }
        }

        return current;
    }
}
