package com.github.leyland.test;

import com.github.leyland.letool.demo.spring.context.support.RequiredPropertieClassPathXmlApplicationContext;
import com.github.leyland.letool.demo.spring.lookup.LookupMethodIn;
import com.github.leyland.letool.demo.spring.source.BeanPostProcessorTest;
import com.github.leyland.letool.demo.spring.source.ingoreInterface.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Arrays;
import java.util.Map;
//import org.testng.annotations.Test;

/**
 * @ClassName <h2>initPropertySources</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class initPropertySourcesTest {

    @Test
    public void initPropertySources() {
        System.setProperty("config", "config");

        //前面讲过，也可以通过JVM属性设置要激活的profile
        System.setProperty("spring.profiles.active", "dev");

        //必备属性，如果注释掉这两个属性，那么启动报错
        System.setProperty("testA", "aaa");
        System.setProperty("testB", "bbb");

        //初始化容器,加入多套环境
        RequiredPropertieClassPathXmlApplicationContext ac = new RequiredPropertieClassPathXmlApplicationContext("spring-${config}.xml",
                "spring-${config}-dev.xml", "spring-${config}-uat.xml");
        System.out.println(ac.getBean("firstSpringSource"));

    }


    @Test
    public void destroy() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config.xml");
        ac.refresh();

    }


    @Test
    public void ph() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-ph.xml");
        System.out.println(ac.getBean("PHTest"));
    }


    @Test
    public void componentsIndex() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-index.xml");
        System.out.println(ac.getBean("indexClass"));
        System.out.println(ac.getBean("indexClass2"));
        System.out.println(ac.getBean("indexClass3"));
        System.out.println(ac.getBean("indexClass4"));
    }


    @Test
    public void beanPostProcessorTest() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-test.xml");
        //手动注入，不会触发BeanPostProcessor回调（这里使用了 registerSingleton(...) 方法来注册单例 Bean，即在整个应用程序上下文中，只创建一个该类型的 Bean 实例。）
        ac.getBeanFactory().registerSingleton("beanPostProcessorTest2", new BeanPostProcessorTest("registerSingleton"));
    }


    @Test
    public void ingoreInterfaceTest() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-test.xml");
        //手动注入，不会触发BeanPostProcessor回调（这里使用了 registerSingleton(...) 方法来注册单例 Bean，即在整个应用程序上下文中，只创建一个该类型的 Bean 实例。）
        ac.getBeanFactory().ignoreDependencyInterface(Ignore.class);
    }


    @Test
    public void ignoreDependencyInterface() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-test.xml");
        System.out.println(ac.getBean("ignoreByName"));
        System.out.println(ac.getBean("ignoreByType"));
        System.out.println(ac.getBean("ignoreByConstructor"));
        System.out.println("---------------------------------------");
        System.out.println(ac.getBean("IgnoreOtherByType"));
        System.out.println(ac.getBean("IgnoreOtherByName"));
        System.out.println(ac.getBean("IgnoreOtherByConstructor"));
    }

    @Test
    public void registerResolvable() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-test.xml");
        System.out.println(ac.getBean("autoRegister"));
    }


    @Test
    public void myRegisterResolvable() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-test.xml");

        System.out.println(ac.getBean("rd"));
        System.out.println(ac.getBean("rdImplA"));
        System.out.println(ac.getBean("rdImplB"));
    }


    @Test
    public void smart() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-smart.xml");
        /*ConfigurableListableBeanFactory factory = ac.getBeanFactory();
        if (factory instanceof DefaultListableBeanFactory) {
            Map<String, Object> beans = ((DefaultListableBeanFactory)factory).getBeansOfType(Object.class);
            for (String name : beans.keySet()) {
                System.out.println(name);
            }
        }*/
    }




    @Test
    public void factoryBean() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-smart.xml");

        //不带&前缀，返回的实际上是getObject的返回值
        System.out.println(ac.getBean("myFactoryBean"));
        System.out.println(ac.getBean("myFactoryBean"));
        System.out.println(ac.getBean("myFactoryBean"));
        System.out.println(ac.getBean("myFactoryBean"));

        //带有&前缀，返回的是自定义的FactoryBean对象本身
        System.out.println(ac.getBean("&myFactoryBean"));
        System.out.println(ac.getBean("&myFactoryBean"));
        System.out.println(ac.getBean("&myFactoryBean"));
        System.out.println(ac.getBean("&myFactoryBean"));

        System.out.println(ac.getBean("mySmartBean.MySmartBeanA"));

        ConfigurableListableBeanFactory factory = ac.getBeanFactory();
        if (factory instanceof DefaultListableBeanFactory) {
            Map<String, Object> beans = ((DefaultListableBeanFactory)factory).getBeansOfType(Object.class);
            for (String name : beans.keySet()) {
                System.out.println(name);
            }
        }
    }



    @Test
    public void dependTest(){
        ClassPathXmlApplicationContext ca = new ClassPathXmlApplicationContext("spring-config-depend.xml");
        System.out.println(ca.getBean("indexClass"));
    }


    @Test
    public void exTest1(){
        try {
            extest11();
        } catch (Exception e){
            System.out.println(e.getMessage());
            e.printStackTrace();
            System.out.println(e);
        }
        System.out.println("继续执行");
        System.out.println("111111111");
        extest11();
    }

    private void extest11(){
        throw new RuntimeException("异常");
    }


    @Test
    public void ThreadTest1(){
        Ticket ticket = new Ticket();
        ticket.run();
    }

    public class Ticket implements Runnable{
        private int ticket = 100;

        Object lock = new Object();
        /*
         * 执行卖票操作
         */
        @Override
        public void run() {
            //每个窗口卖票的操作
            //窗口 永远开启
            while(true){
                synchronized (lock) {
                    if(ticket > 0){//有票 可以卖
                        //出票操作
                        //使用sleep模拟一下出票时间
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            // TODO Auto‐generated catch block
                            e.printStackTrace();
                        }
                        //获取当前线程对象的名字
                        String name = Thread.currentThread().getName();
                        System.out.println(name+"正在卖:" + --ticket);
                    }
                }
            }
        }
    }


    @Test
    public void testStatic1(){
        ApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-util.xml");
        System.out.println(ac.getBean("applicationContextUtil"));
    }


    @Test
    public void testStatic2() {

        test11();
    }

    void test11(){
        try {
            test22();
            test33();
        } catch (Exception e) {
            //如果多个方法进行try catch，就不确定到底是哪一个抛出去的。方法调用栈信息只会截止到 catch 这里，不会找到 test22(); 或者 test33(); 中。
            throw new RuntimeException("test11 捕获异常" + e);
        }

        /*test22();*/
    }


    void test22() /*throws Exception*/{
        throw new RuntimeException("错误22");
    }

    void test33() /*throws Exception*/{
        throw new RuntimeException("错误33");
    }


    @Test
    public void lookupmethodNi() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-lookup.xml");
        System.out.println(Arrays.toString(ac.getBeanDefinitionNames()));
        LookupMethodIn.LookupMethodInA lookupMethodInA = ac.getBean("lookupMethodInA", LookupMethodIn.LookupMethodInA.class);
        System.out.println("获取lookupMethodInB，同一个对象");
        System.out.println("lookupMethodInB：" + lookupMethodInA.getLookupMethodInB());
        System.out.println("lookupMethodInB：" + lookupMethodInA.getLookupMethodInB());
        System.out.println("lookupMethodInB：" + lookupMethodInA.getLookupMethodInB());
        System.out.println("获取lookupMethodInC，不同的对象");
        System.out.println("lookupMethodInC：" + lookupMethodInA.getLookupMethodInC());
        System.out.println("lookupMethodInC：" + lookupMethodInA.getLookupMethodInC());
        System.out.println("lookupMethodInC：" + lookupMethodInA.getLookupMethodInC());

        //实际上lookupMethodInA的lookupMethodInC属性根本没有被注入过，每一次的getLookupMethodInC都是直接找容器要对象
        //而由于lookupMethodInC被设置为prototype，因此每一次都会获取新的对象
        System.out.println("lookupMethodInA：" + lookupMethodInA);
    }

}
