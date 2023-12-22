package com.github.leyland.letool.demo.spring.source.smart;

/**
 * @ClassName <h2>Custom</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class Custom {
    private String property1;
    private double property2;


    public Custom(String property1, double property2) {
        this.property1 = property1;
        this.property2 = property2;
    }

    @Override
    public String toString() {
        return "Custom{" +
                "property1='" + property1 + '\'' +
                ", property2=" + property2 +
                '}';
    }
}
