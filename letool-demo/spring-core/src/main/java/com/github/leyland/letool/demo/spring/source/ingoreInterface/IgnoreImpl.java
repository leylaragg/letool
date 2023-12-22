package com.github.leyland.letool.demo.spring.source.ingoreInterface;

/**
 * @ClassName <h2>IgnoreImpl</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class IgnoreImpl implements Ignore {

    private PoJoA poJoA;
    private PoJoB poJoB;

    @Override
    public void setPoJoA(PoJoA poJoA) {
        this.poJoA = poJoA;
    }
    @Override
    public void setPoJoB(PoJoB poJoB) {
        this.poJoB = poJoB;
    }

    public IgnoreImpl(PoJoA poJoA, PoJoB poJoB) {
        this.poJoA = poJoA;
        this.poJoB = poJoB;
    }

    public IgnoreImpl() {
    }

    @Override
    public String toString() {
        return "IgnoreImpl{" +
                "poJoA=" + poJoA +
                ", poJoB=" + poJoB +
                '}';
    }

    public static class PoJoA{

    }
    public static class PoJoB{

    }
}
