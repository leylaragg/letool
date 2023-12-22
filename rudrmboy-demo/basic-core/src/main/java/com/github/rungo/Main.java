package com.github.rungo;

import com.github.rungo.rudrmboy.demo.basic.core.variate.DataClass;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        //ex1();
        try {
            ex1();
            System.out.println("111111111");
        }catch (Exception e){
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
        System.out.println("222222222");
    }

    public static void ex1() {
        throw new RuntimeException("ex1() 运行时异常");
    }


    @Test
    public void test1(){
        // 创建类的对象
        DataClass dc = new DataClass();
        // 对象名.变量名调用实例变量（全局变量）
        System.out.println(dc.name);
        System.out.println(dc.age);

        // 对象名.变量名调用静态变量（类变量）
        System.out.println(dc.website);
        System.out.println(dc.URL);

        // 类名.变量名调用静态变量（类变量）
        System.out.println(DataClass.website);
        System.out.println(DataClass.URL);
    }

    @Test
    public void Test2() {
        int a = 7;
        if (5 > 3) {
            int s = 3; // 声明一个 int 类型的局部变量
            System.out.println("s=" + s);
            System.out.println("a=" + a);
        }
        System.out.println("a=" + a);

    }


    @Test
    public void Test3() {
        testFun(3);
    }

    public static void testFun(int n) {
        System.out.println("n=" + n);
    }


    @Test
    public void Test4() {
        test();
    }

    public static void test() {
        try {
            System.out.println("Hello!Exception!");
        } catch (Exception e) { // 异常处理块，参数为 Exception 类型
            e.printStackTrace();
        }
    }

    @Test
    public void Test5() {
        //初始化一个byte类型的变量并赋予初始值为20
        byte a = 20;

        //初始化一个short类型的变量并赋予初始值为10
        short b = 10;

        //初始化一个int类型的变量并赋予初始值为30
        int c = 30;

        //初始化一个long类型的变量并赋予初始值为40
        long d = 40;

        long sum = a + b + c + d;
        System.out.println("20 + 10 + 30 + 40 = " + sum);
    }


    public class User {
        public String name;
        public int age;

        public User() {
        }

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }
    }

    @Test
    public void Test6() {

        String[] urls = { "https://www.yuque.com/", "https://github.com/", "https://spring.io/" };
        // 使用foreach循环来遍历数组元素，其中 book 将会自动迭代每个数组元素
        for (String url : urls) {
            url = "https://www.yuque.com/leyland.wang/";
            System.out.println(url);
        }
        System.out.println(urls[0]);

    }

    @Test
    public void Test7() {
        User [] users = new User[]{
                new User("Rungo", 18),
                new User("Leya", 19),
                new User("Leyland", 20)
        };
        for (User user : users) {
            user = new User("Tom", 21);
            System.out.println(user);
        }
        System.out.println(users[0]);
    }

    @Test
    public void Test8() {
        label1: for (int x = 0; x < 5; x++) {
            for (int y = 5; y > 0; y--) {
                if (y == x) {
                    break label1;
                }
                System.out.println(x + "," + y); //当 y == x 执行continue的时候，会直接跳到外层循环进行下一次执行。
            }
        }
        System.out.println("Game Over!");
    }

    @Test
    public void Test9() {
        int[] number = {1, 2, 3, 5, 8};
        System.out.println("获取第一个元素：" + number[0]);
        System.out.println("获取最后一个元素：" + number[number.length-1]);
        System.out.println("获取第6个元素：" + number[5]);
    }



    @Test
    public void Test10() {
        Thread thread = new Thread();
        System.out.println("Current Thread: " + thread.getName());
    }


    @Test
    public void Test11() {
        System.out.println(new BigDecimal(1000000).compareTo(new BigDecimal(1000000)));
        System.out.println(new BigDecimal(1000000).compareTo(new BigDecimal(2000000)));
        System.out.println(new BigDecimal(1000000).compareTo(new BigDecimal(100000)));
        System.out.println(new BigDecimal(1000000).compareTo(new BigDecimal(-1)));
        
    }

    @Test
    public void Test12() {
        String str = "abc";
        StringTes stringTes = new StringTes();
        stringTes.name = "999";
        //testString(str, stringTes);
        testString(stringTes.name, stringTes);
        System.out.println(str);
        System.out.println(stringTes.name);
    }

    public String testString(String str, StringTes stringTes){
        str += str;
        stringTes.name = "666";
        return str;
    }

    public class StringTes{
        public String name;
    }

    @Test
    public void Test13() {

    }

    @Test
    public void Test14() {

    }

    @Test
    public void Test15() {

    }

    @Test
    public void Test16() {

    }

    @Test
    public void Test17() {

    }

    @Test
    public void Test18() {

    }

    @Test
    public void Test19() {

    }

    @Test
    public void Test20() {

    }

}