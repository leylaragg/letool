package com.github.leyland.letool.demo.spring.source.ingoreInterface;

/**
 * @ClassName <h2>IgnoreOther</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class IgnoreOther {

    private IgnoreImpl.PoJoA poJoA;
    private IgnoreImpl.PoJoB poJoB;

    public void setPoJoA(IgnoreImpl.PoJoA poJoA) {
        this.poJoA = poJoA;
    }

    public void setPoJoB(IgnoreImpl.PoJoB poJoB) {
        this.poJoB = poJoB;
    }

    public IgnoreOther(IgnoreImpl.PoJoA poJoA, IgnoreImpl.PoJoB poJoB) {
        this.poJoA = poJoA;
        this.poJoB = poJoB;
    }

    public IgnoreOther() {
    }

    @Override
    public String toString() {
        return "IgnoreOther{" +
                "poJoA=" + poJoA +
                ", poJoB=" + poJoB +
                '}';
    }
}
