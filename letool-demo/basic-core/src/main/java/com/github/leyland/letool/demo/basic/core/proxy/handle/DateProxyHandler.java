package com.github.leyland.letool.demo.basic.core.proxy.handle;

import com.github.leyland.letool.demo.basic.core.anno.TypeMapper;
import com.github.leyland.letool.demo.basic.core.proxy.DateProxy;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @ClassName <h2>DateProxyHandler</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class DateProxyHandler implements MethodInterceptor {
    private DateProxy target;

    public DateProxyHandler(DateProxy target) {
        this.target = target;
    }

    public static DateProxy createProxy(DateProxy target) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(DateProxy.class);
        enhancer.setCallback(new DateProxyHandler(target));
        return (DateProxy) enhancer.create();
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        if (method.getName().equals("getApplicantIDEndDate")) {
            Date originalDate = (Date) method.invoke(target, args);
            TypeMapper annotation = target.getClass().getDeclaredMethod("getApplicantIDEndDate").getAnnotation(TypeMapper.class);
            if (annotation != null) {
                String dateFormat = annotation.dateFormat();
                if (originalDate != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
                    return sdf.format(originalDate);
                }
            }
        }
        return method.invoke(target, args);
    }
}
