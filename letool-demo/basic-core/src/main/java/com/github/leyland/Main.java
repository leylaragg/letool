package com.github.leyland;

import com.github.leyland.letool.demo.basic.core.proxy.DateProxy;
import com.github.leyland.letool.demo.basic.core.proxy.handle.DateProxyHandler;
import com.github.leyland.letool.demo.basic.core.variate.DataClass;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

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

        DateProxy original = new DateProxy();
        original.setApplicantIDEndDate(new Date());
        DateProxy proxy = DateProxyHandler.createProxy(original);

        //代理不会改变返回值，没什么卵用
        System.out.println(proxy.getApplicantIDEndDate());
    }

    @Test
    public void Test14() {

        System.out.println("leyland".toString());

        BigDecimal bigDecimal = new BigDecimal(1);

        int i = bigDecimal.compareTo(BigDecimal.ZERO);

        System.out.println(i);

        System.out.println(bigDecimal.toString());

    }

    @Test
    public void Test15() {

        CopyOnWriteArrayList<Integer> integers = new CopyOnWriteArrayList<Integer>(Arrays.asList(1, 2, 3));
        //获取迭代器
        Iterator<Integer> iterator = integers.iterator();
        //是否存在下一个元素
        while (iterator.hasNext()) {
            //使用集合的方法 移除第一个元素，此时不会在next()方法中抛出异常1
            Integer remove = integers.remove(0);
            System.out.println("被移除的: " + remove);
            //获取下一个元素,被移除的元素还是能获取到,正是由于Copy-On-Write技术造成的
            Object next = iterator.next();
            System.out.println("获取到的: " + next);
        }

        integers.forEach(System.out::println);

    }

    @Test
    public void Test16() {

        ArrayList<Integer> integers = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        //获取迭代器
        Iterator<Integer> iterator = integers.iterator();
        //是否存在下一个元素
        while (iterator.hasNext()) {
            //使用集合的方法 移除一个元素，此时会在next()方法中抛出异常
            integers.remove(0);
            //获取下一个元素
            Object next = iterator.next();
        }

    }

    @Test
    public void Test17() {
        StringBuffer jdbcSql = new StringBuffer();
        jdbcSql.append(" (SELECT ");
        jdbcSql.append("  distinct  " +
                " a.CHDRCOY  " +//公司号
                " ,a.CHDRNUM  " +//8位的保单号
                " from lcndta.p_chdrpf a  " +
                " where a.validflag = '1'   " +
                " and exists( select 1 from  lcndta.p_aglfpf b where a.agntcoy = b.agntcoy and a.agntnum = b.agntnum and b.tsalesunt = 'BK001') " +
                " and exists (  select 1  from  lcndta.p_ptrnpf p  where  p.CHDRCOY = a.CHDRCOY and p.CHDRNUM = a.CHDRNUM AND p.validflag <> '2'  " +
                " and p.transtime between '" + "2024-06-17 00:00:00" + "'  and '" + "2024-06-17 23:59:59" + "'" +
                " and p.batctrcde in ('T600','T6A0','T642')   " +
                ")" +
                " WITH UR) "
        );
        jdbcSql.append(" union all ");
        jdbcSql.append(" (SELECT ");
        jdbcSql.append("  distinct  " +
                "   a.CHDRCOY  " +//公司号
                "  ,a.CHDRNUM  " +//8位的保单号
                "  from lcndta.p_chdrpf a  " +
                "   where a.validflag = '1'   " +
                "  and exists(  select 1 from  lcndta.p_aglfpf b where  a.agntcoy = b.agntcoy  and a.agntnum = b.agntnum and b.tsalesunt = 'BK001'  ) " +
                "  and exists( select 1 from  lcndta.p_pletpf p2  where  p2.effdate <> 99999999  and p2.CHDRCOY = a.CHDRCOY   and p2.CHDRNUM = a.CHDRNUM  " +
                " and p2.batctrcde in ('T600','T6A0','T642')  " +
                "  and  p2.transtime between '" + "2024-06-17 00:00:00" + "'  and '" + "2024-06-17 23:59:59" + "'  ) " + //--#结束时间#
                " WITH UR) "
        );
        System.out.println(jdbcSql.toString());

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