package com.github.leyland.letool.thread.example.jvm;

import org.junit.Test;

/**
 * @ClassName <h2>StringTableExample</h2>
 * @Description
 * @Author rungo
 * @Date 3/26/2025
 * @Version 1.0
 **/
public class StringTableExample {

    @Test
    public void test1() {
        System.out.println("1");
        System.out.println("2");
        System.out.println("3");
        System.out.println("4");
        System.out.println("5");
        System.out.println("6");
        System.out.println("7");
        System.out.println("8");
        System.out.println("9");
        System.out.println("10");
        System.out.println("1");
        System.out.println("2");
        System.out.println("3");
        System.out.println("4");
        System.out.println("5");
        System.out.println("6");
        System.out.println("7");
        System.out.println("8");
        System.out.println("9");
        System.out.println("10");
    }

    @Test
    public void test2() {
        // 都是常量，前端编译期会进行代码优化
        // 通过idea直接看对应的反编译的class文件，会显示 String s1 = "abc"; 说明做了代码优化
        String s1 = "a" + "b" + "c";
        String s2 = "abc";
        // true，有上述可知，s1和s2实际上指向字符串常量池中的同一个值
        System.out.println(s1 == s2);
    }

    @Test
    public void test3(){
        String s1 = "a";
        String s2 = "b";
        String s3 = "ab";
        String s4 = s1 + s2;

        /*

            代码s1 + s2的执行细姐：
                1. StringBuilder s = new StringBuilder();
                2. s.append("a");
                3. s.append("b")
                4. s.toString();
                注意：StringBuilder#toString() 约等于 new String("ab");
                因为该方法它不会将生成的字符串放到字符串常量池中。

        在JDB5.0后使用的是StringBuilder，之前使用的是StringBuilder
         */

        System.out.println(s3 == s4);
    }

    @Test
    public void test4(){
        String s1 = "a";
        String s2 = new String("a");
        System.out.println(s1 == s2);
    }

    @Test
    public void test5(){
        String s1 = "abc";
        String s2 = "abc";
        String s3 = new String("abc");
        System.out.println(s1 == s2);
        System.out.println(s1 == s3);
    }


    @Test
    public void test6(){
        String s0 = "beijing";		//字符串常量池中存放beijing，s0 直接指向字符串常量池
        String s1 = "bei";			//字符串常量池中存放bei，s1 直接指向字符串常量池
        String s2 = "jing";			//字符串常量池中存放jing，s2 直接指向字符串常量池
        String s3 = s1 + s2;
        System.out.println(s0 == s3); // false s3指向对象实例，s0指向字符串常量池中的"beijing"

        String s7 = "shanxi";
        final String s4 = "shan";
        final String s5 = "xi";
        String s6 = s4 + s5;
        System.out.println(s6 == s7); // true s4和s5是final修饰的，编译期就能确定s6的值了
    }

    @Test
    public void test7(){
        long start = System.currentTimeMillis();

        method1(100000);      //花费时间：913ms
//        method2(100000);      //花费时间：4ms
//        method3(100000);      //花费时间：3ms

        long end = System.currentTimeMillis();
        System.out.println("花费时间：" + (end - start) + "ms");
    }

    public void method1(int highLevel) {
        String s = "";
        for (int i = 0; i < highLevel; i++) {
            s = s + "a";    //每次循环都会创建一个 StringBuilder
        }
    }

    public void method2(int highLevel) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < highLevel; i++) {
            s.append("a");    //只会创建一个 StringBuilder
        }
    }

    public void method3(int highLevel) {
        StringBuffer s = new StringBuffer();
        for (int i = 0; i < highLevel; i++) {
            s.append("a");    //只会创建一个 StringBuilder
        }
    }


    @Test
    public void test8() {
        //字节码指令：lcd ，放入字符串常量池
        String str = new String("ab");
    }

    @Test
    public void test9() {
        String str = new String("a") + new String("b");
    }

    @Test
    public void test10() {
        String s1 = "ab";
        char[] chars = {'a', 'b'};
        String s2 = new String(chars);
        System.out.println(s1 == s2);
    }


    @Test
    public void test11() {
        String s1 = new String("1");
        s1 = s1.intern();
//        s1.intern();      // false
        String s2 = "1";
        System.out.println(s1 == s2);

        String s3 = new String("1") + new String("1");
//        s3 = s3.intern();
        s3.intern();        // true
        String s4 = "11";
        System.out.println(s3 == s4);
    }

    @Test
    public void test12() {
        String s3 = new String("1") + new String("1");
        String s4 = "11";
        s3.intern();
        System.out.println(s3 == s4);
    }


    @Test
    public void test13() {
        StringBuffer s1 = new StringBuffer("abc");
        s1.toString().intern();
        String s2 = s1.toString().intern();
        String s3 = s1.toString().intern();
        System.out.println(s2 == s3);   //true
    }


    @Test
    public void test14() {
        int i= 1;//line 2
        Object obj = new Object();//line 3
        foo(obj);
    }//Line 9
    private void foo(Object param) {//line 6
        String str = param.toString();//line 7
        System.out.println(str);
    }//Line 8


}
