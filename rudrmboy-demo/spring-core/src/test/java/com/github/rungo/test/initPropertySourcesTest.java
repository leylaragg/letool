package com.github.rungo.test;

import com.github.rungo.rudrmboy.demo.spring.context.support.RequiredPropertieClassPathXmlApplicationContext;
import com.github.rungo.rudrmboy.demo.spring.source.BeanPostProcessorTest;
import com.github.rungo.rudrmboy.demo.spring.source.ingoreInterface.Ignore;
import com.github.rungo.rudrmboy.demo.spring.source.ingoreInterface.IgnoreOther;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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
    }
}
