package com.github.leyland.letool.tool.mapper;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 类型转换工具类
 * 支持常见数据类型之间的自动转换
 *
 * @author leyland
 * @date 2025-01-08
 */
public class TypeConverterUtil {

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_MAP = new HashMap<>();

    static {
        PRIMITIVE_WRAPPER_MAP.put(boolean.class, Boolean.class);
        PRIMITIVE_WRAPPER_MAP.put(byte.class, Byte.class);
        PRIMITIVE_WRAPPER_MAP.put(char.class, Character.class);
        PRIMITIVE_WRAPPER_MAP.put(double.class, Double.class);
        PRIMITIVE_WRAPPER_MAP.put(float.class, Float.class);
        PRIMITIVE_WRAPPER_MAP.put(int.class, Integer.class);
        PRIMITIVE_WRAPPER_MAP.put(long.class, Long.class);
        PRIMITIVE_WRAPPER_MAP.put(short.class, Short.class);
    }

    private static final Map<Class<?>, Object> DEFAULT_VALUE_MAP = new HashMap<>();

    static {
        DEFAULT_VALUE_MAP.put(boolean.class, false);
        DEFAULT_VALUE_MAP.put(Boolean.class, false);
        DEFAULT_VALUE_MAP.put(byte.class, (byte) 0);
        DEFAULT_VALUE_MAP.put(Byte.class, (byte) 0);
        DEFAULT_VALUE_MAP.put(char.class, '\0');
        DEFAULT_VALUE_MAP.put(Character.class, '\0');
        DEFAULT_VALUE_MAP.put(double.class, 0.0);
        DEFAULT_VALUE_MAP.put(Double.class, 0.0);
        DEFAULT_VALUE_MAP.put(float.class, 0.0f);
        DEFAULT_VALUE_MAP.put(Float.class, 0.0f);
        DEFAULT_VALUE_MAP.put(int.class, 0);
        DEFAULT_VALUE_MAP.put(Integer.class, 0);
        DEFAULT_VALUE_MAP.put(long.class, 0L);
        DEFAULT_VALUE_MAP.put(Long.class, 0L);
        DEFAULT_VALUE_MAP.put(short.class, (short) 0);
        DEFAULT_VALUE_MAP.put(Short.class, (short) 0);
    }

    /**
     * 将源值转换为目标类型
     *
     * @param sourceValue 源值
     * @param targetType  目标类型
     * @param <T>         目标类型泛型
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object sourceValue, Class<T> targetType) {
        if (sourceValue == null) {
            return (T) DEFAULT_VALUE_MAP.get(targetType);
        }

        // 如果已经是目标类型或其包装类，直接返回
        if (targetType.isInstance(sourceValue)) {
            return (T) sourceValue;
        }

        // 处理原始类型和包装类型的转换
        if (isPrimitiveOrWrapper(sourceValue.getClass()) && isPrimitiveOrWrapper(targetType)) {
            return convertPrimitive(sourceValue, targetType);
        }

        // 处理 String 到其他类型的转换
        if (sourceValue instanceof String) {
            return convertFromString((String) sourceValue, targetType);
        }

        // 处理其他类型到 String 的转换
        if (targetType == String.class) {
            return (T) sourceValue.toString();
        }

        // 处理数字类型之间的转换
        if (sourceValue instanceof Number && Number.class.isAssignableFrom(targetType)) {
            return convertNumber((Number) sourceValue, targetType);
        }

        // 无法转换时返回 null
        return null;
    }

    /**
     * 判断是否为原始类型或包装类型
     */
    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || PRIMITIVE_WRAPPER_MAP.containsValue(type);
    }

    /**
     * 原始类型转换
     */
    @SuppressWarnings("unchecked")
    private static <T> T convertPrimitive(Object sourceValue, Class<T> targetType) {
        if (targetType == boolean.class || targetType == Boolean.class) {
            return (T) Boolean.valueOf(sourceValue.toString());
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return (T) Byte.valueOf(sourceValue.toString());
        }
        if (targetType == char.class || targetType == Character.class) {
            String str = sourceValue.toString();
            return (T) Character.valueOf(str.isEmpty() ? '\0' : str.charAt(0));
        }
        if (targetType == double.class || targetType == Double.class) {
            return (T) Double.valueOf(sourceValue.toString());
        }
        if (targetType == float.class || targetType == Float.class) {
            return (T) Float.valueOf(sourceValue.toString());
        }
        if (targetType == int.class || targetType == Integer.class) {
            return (T) Integer.valueOf(sourceValue.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            return (T) Long.valueOf(sourceValue.toString());
        }
        if (targetType == short.class || targetType == Short.class) {
            return (T) Short.valueOf(sourceValue.toString());
        }
        return null;
    }

    /**
     * 从字符串转换
     */
    @SuppressWarnings("unchecked")
    private static <T> T convertFromString(String str, Class<T> targetType) {
        if (str == null || str.isEmpty()) {
            return (T) DEFAULT_VALUE_MAP.get(targetType);
        }

        try {
            if (targetType == boolean.class || targetType == Boolean.class) {
                return (T) Boolean.valueOf(str);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return (T) Byte.valueOf(str);
            }
            if (targetType == char.class || targetType == Character.class) {
                return (T) Character.valueOf(str.charAt(0));
            }
            if (targetType == double.class || targetType == Double.class) {
                return (T) Double.valueOf(str);
            }
            if (targetType == float.class || targetType == Float.class) {
                return (T) Float.valueOf(str);
            }
            if (targetType == int.class || targetType == Integer.class) {
                return (T) Integer.valueOf(str);
            }
            if (targetType == long.class || targetType == Long.class) {
                return (T) Long.valueOf(str);
            }
            if (targetType == short.class || targetType == Short.class) {
                return (T) Short.valueOf(str);
            }
            if (targetType == BigDecimal.class) {
                return (T) new BigDecimal(str);
            }
            if (targetType == BigInteger.class) {
                return (T) new BigInteger(str);
            }
            if (targetType == Date.class) {
                // 尝试多种日期格式
                String[] patterns = {
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd",
                    "yyyy/MM/dd HH:mm:ss",
                    "yyyy/MM/dd",
                    "HH:mm:ss"
                };
                for (String pattern : patterns) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                        return (T) sdf.parse(str);
                    } catch (ParseException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            // 转换失败返回默认值
        }
        return (T) DEFAULT_VALUE_MAP.get(targetType);
    }

    /**
     * 数字类型转换
     */
    @SuppressWarnings("unchecked")
    private static <T> T convertNumber(Number number, Class<T> targetType) {
        if (targetType == Byte.class || targetType == byte.class) {
            return (T) Byte.valueOf(number.byteValue());
        }
        if (targetType == Short.class || targetType == short.class) {
            return (T) Short.valueOf(number.shortValue());
        }
        if (targetType == Integer.class || targetType == int.class) {
            return (T) Integer.valueOf(number.intValue());
        }
        if (targetType == Long.class || targetType == long.class) {
            return (T) Long.valueOf(number.longValue());
        }
        if (targetType == Float.class || targetType == float.class) {
            return (T) Float.valueOf(number.floatValue());
        }
        if (targetType == Double.class || targetType == double.class) {
            return (T) Double.valueOf(number.doubleValue());
        }
        if (targetType == BigDecimal.class) {
            return (T) BigDecimal.valueOf(number.doubleValue());
        }
        if (targetType == BigInteger.class) {
            return (T) BigInteger.valueOf(number.longValue());
        }
        return (T) number;
    }
}
