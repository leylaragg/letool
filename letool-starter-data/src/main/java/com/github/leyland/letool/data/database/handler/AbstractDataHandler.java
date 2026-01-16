package com.github.leyland.letool.data.database.handler;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @ClassName <h2>AbstractDataHandler</h2>
 * @Description     抽象数据处理器
 *                      提供通用的数据处理方法和工具
 * @Author leyland
 * @Date 2026/01/16
 * @Version 1.0
 **/
@Slf4j
public abstract class AbstractDataHandler implements DataHandler {

    /**
     * 从数据中安全获取字符串值
     *
     * @param data 数据Map
     * @param key 键名
     * @return 字符串值
     */
    protected String getSafeString(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) {
            return null;
        }
        return data.get(key).toString();
    }

    /**
     * 从数据中安全获取整数值
     *
     * @param data 数据Map
     * @param key 键名
     * @return 整数值
     */
    protected Integer getSafeInteger(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) {
            return null;
        }

        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.debug("无法解析整数字段 {}: {}", key, value);
                return null;
            }
        }

        return null;
    }

    /**
     * 从数据中安全获取长整数值
     *
     * @param data 数据Map
     * @param key 键名
     * @return 长整数值
     */
    protected Long getSafeLong(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) {
            return null;
        }

        Object value = data.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.debug("无法解析长整数字段 {}: {}", key, value);
                return null;
            }
        }

        return null;
    }

    /**
     * 从数据中安全获取布尔值
     *
     * @param data 数据Map
     * @param key 键名
     * @return 布尔值
     */
    protected Boolean getSafeBoolean(Map<String, Object> data, String key) {
        if (data == null || data.get(key) == null) {
            return null;
        }

        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String strValue = ((String) value).toLowerCase();
            return "true".equals(strValue) || "1".equals(strValue) || "yes".equals(strValue);
        } else if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        return null;
    }

    /**
     * 联查单个表的数据
     *
     * @param queryKey 查询键
     * @param params 查询参数
     * @param context 查询上下文
     * @return 查询结果列表
     */
    protected List<Map<String, Object>> joinQuery(String queryKey, Map<String, Object> params, QueryContext context) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (context.getQueryExecutor() == null) {
            log.warn("查询执行器未配置，跳过联查: {}", queryKey);
            return result;
        }

        try {
            context.getQueryExecutor().streamQuery(queryKey, params, data -> result.add(data));
            log.debug("联查询表 {}: 返回{}条记录", queryKey, result.size());
        } catch (Exception e) {
            log.error("联查询表失败: queryKey={}", queryKey, e);
        }

        return result;
    }
}
