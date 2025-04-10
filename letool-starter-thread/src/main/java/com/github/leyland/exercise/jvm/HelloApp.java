package com.github.leyland.exercise.jvm;

import java.nio.channels.FileChannel;

/**
 * @ClassName <h2>HelloApp</h2>
 * @Description
 * @Author rungo
 * @Date 3/20/2025
 * @Version 1.0
 **/
public class HelloApp {
    static {
        num = 10;//变量赋值可以正常编译通过
//        System.out.println(num);//编译器提示“非法前向引用"
    }

    static int num = 1;

    public static void main(String[] args) {

        int a = 6;

        int b = 9;

        System.out.println(a + b);

        System.out.println(HelloApp.num);

        System.out.println(String.class.getClassLoader());

        System.out.println(Thread.currentThread().getContextClassLoader());

    }


    public int test() {
        int x = 0;
        int y = 1;
        return x + y;
    }


}
