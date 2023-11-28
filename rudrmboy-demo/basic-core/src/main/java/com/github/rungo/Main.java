package com.github.rungo;

import com.github.rungo.rudrmboy.demo.basic.core.variate.DataClass;
import org.junit.jupiter.api.Test;

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

}