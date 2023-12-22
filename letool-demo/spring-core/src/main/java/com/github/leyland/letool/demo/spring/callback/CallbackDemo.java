package com.github.leyland.letool.demo.spring.callback;

/**
 * @ClassName <h2>CallbackDemo</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class CallbackDemo {
    public static void main(String[] args) {
        Caller caller = new Caller();
        Callback callback = new CallbackImpl();
        System.out.println("Before register callback.");
        caller.registerCallback(callback);
        System.out.println("After register callback.");
    }
}