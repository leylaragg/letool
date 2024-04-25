package com.github.leyland.letool.demo.spring.mvc.test;

import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @ClassName <h2>TestDate</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
public class TestDate {
   /* @Value("2020-12-12")
    private Date date;

    @PostConstruct
    public void test() {
        System.out.println(date);
    }*/

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException, InstantiationException, ParseException {
        Object o = new String("123");
        System.out.println(o.getClass());

        for (Field declaredField : User.class.getDeclaredFields()) {
            System.out.println(declaredField);
            System.out.println(declaredField.getName());
            System.out.println(declaredField.getType());
            System.out.println(declaredField.getType().getName());
        }

        System.out.println("-----------------------------------------------------------");

        User user = User.class.newInstance();
//        User user = new User();
        Field nameField = User.class.getDeclaredField("name");
        nameField.setAccessible(true);
        System.out.println(nameField.get(user));


        List<String> lsitStrs = new ArrayList<>();
        lsitStrs.add("123");

        System.out.println(lsitStrs.getClass().getFields());

        System.out.println();

        System.out.println(lsitStrs.get(0).getClass().isAssignableFrom(String.class));

        System.out.println(List.class.isAssignableFrom(ArrayList.class));


        System.out.println("----------------------------------------------------------------");


        for (Field field : User.class.getDeclaredFields()) {
            System.out.println(field);
            System.out.println("========== " + field.getDeclaringClass());
        }

        System.out.println("----------------------------------------------------------------");

        // 获取MyTestClass类中名为"list"的字段
        Field listField = User.class.getDeclaredField("lsitStrs");
        // 获取该字段的类型信息，getGenericType()方法能够获取带有泛型的类型信息
        Type genericType = listField.getGenericType();
        // 但我们实际上需要获取返回值类型中的泛型信息，所以要进一步判断，即判断获取的返回值类型是否是参数化类型ParameterizedType
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            // 获取成员变量的泛型类型信息
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            for (Type actualTypeArgument : actualTypeArguments) {
                Class fieldArgClass = (Class) actualTypeArgument;
                System.out.println("成员变量的泛型信息：" + fieldArgClass);
            }
        }


        int [] ints = new int[3];

        System.out.println(ints.length);

        System.out.println(new BigDecimal(20240422));

        int a = 999;
        intMethod(a);
        System.out.println(a);

        Integer b = 111;
        intMethod(b);
        System.out.println(b);


        String str = "asjdkhaj";
        stringMethod(str);
        System.out.println(str);

        System.out.println((new SimpleDateFormat("yyyy-MM-dd").parse("2024-04-23")).toString());

        System.out.println((new SimpleDateFormat("yyyy-MM-dd").parse("2024-04-23")).toString());




    }


    private static int intMethod(int a) {
        a = 666;
        return a;
    }

    private static Integer intMethod(Integer b) {
        b = 666;
        return b;
    }

    private static String stringMethod(String str) {
        str = "Hello World";
        return str;
    }

}

