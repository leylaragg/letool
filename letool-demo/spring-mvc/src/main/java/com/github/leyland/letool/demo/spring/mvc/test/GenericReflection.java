package com.github.leyland.letool.demo.spring.mvc.test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName <h2>GenericReflection</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class GenericReflection {

    public static void main(String[] args) throws NoSuchFieldException {
        List<String> listStrs = new ArrayList<>();
        // 获取List<String>的泛型类型
        Type genericType = getFieldGenericType("listStrs");
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length > 0) {
                Type elementType = typeArguments[0];
                System.out.println("List<String>的泛型类型是：" + elementType.getTypeName());
            }
        }
    }

    public static Type getFieldGenericType(String fieldName) throws NoSuchFieldException {
        // 获取字段
        Field field = GenericReflection.class.getDeclaredField(fieldName);
        // 获取字段的泛型类型
        Type genericType = field.getGenericType();
        return genericType;
    }
}
