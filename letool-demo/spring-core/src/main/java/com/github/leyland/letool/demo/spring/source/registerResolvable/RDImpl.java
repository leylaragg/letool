package com.github.leyland.letool.demo.spring.source.registerResolvable;

/**
 * @ClassName <h2>RDImpl</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class RDImpl {

    /**
     * 尝试自动注入该属性
     */
    private ResolvableDependency resolvableDependency;

    public void setResolvableDependency(ResolvableDependency resolvableDependency) {
        this.resolvableDependency = resolvableDependency;
    }

    @Override
    public String toString() {
        return "RDImpl{" +
                "resolvableDependency=" + resolvableDependency +
                '}';
    }

    public static class RDImplA implements ResolvableDependency {}


    public static class RDImplB implements ResolvableDependency {}
}
