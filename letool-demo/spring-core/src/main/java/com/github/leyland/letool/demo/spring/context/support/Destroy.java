package com.github.leyland.letool.demo.spring.context.support;

import org.springframework.beans.factory.DisposableBean;

/**
 * @ClassName <h2>Destroy</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class Destroy {

    /**
     * 实现了DisposableBean接口
     */
    public static class Destroy1 implements DisposableBean {
        public void destroy() throws Exception {
            System.out.println("destroy1 -- destroy() 销毁回调");
        }
        public void destroy2() throws Exception {
            System.out.println("destroy1 -- destroy2() 销毁回调");
        }
    }

    /**
     * 定义了destroy-method属性
     */
    public static class Destroy2 {
        public void destroy() throws Exception {
            System.out.println("destroy2销毁回调");
        }
    }

    /**
     * 没有定义回调方法
     */
    public static class Destroy3 {
        public void destroy() throws Exception {
            System.out.println("destroy3销毁回调");
        }
    }
}

