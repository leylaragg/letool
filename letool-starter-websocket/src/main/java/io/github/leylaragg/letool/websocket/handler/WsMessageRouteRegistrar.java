package io.github.leylaragg.letool.websocket.handler;

import io.github.leylaragg.letool.websocket.annotation.WsAuth;
import io.github.leylaragg.letool.websocket.annotation.WsMessageMapping;
import io.github.leylaragg.letool.websocket.core.WsMessage;
import io.github.leylaragg.letool.websocket.core.WsSession;
import io.github.leylaragg.letool.websocket.exception.WsException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 扫描并注册 {@link WsMessageMapping} 方法的注册器。
 */
public final class WsMessageRouteRegistrar implements SmartInitializingSingleton {

    private final WsMessageRouter router;
    private final ConfigurableListableBeanFactory beanFactory;

    /**
     * 创建注解路由注册器。
     *
     * @param router 消息路由器
     */
    public WsMessageRouteRegistrar(WsMessageRouter router) {
        this(router, null);
    }

    /**
     * 创建可在 Spring 容器启动完成后自动扫描的注解路由注册器。
     *
     * @param router 消息路由器
     * @param beanFactory Spring Bean 工厂
     */
    public WsMessageRouteRegistrar(
            WsMessageRouter router,
            ConfigurableListableBeanFactory beanFactory) {
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.beanFactory = beanFactory;
    }

    /**
     * 在全部非懒加载单例创建完成后扫描业务 Bean，确保路由问题在启动期暴露。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (beanFactory == null) {
            return;
        }
        Set<Object> registeredBeans = Collections.newSetFromMap(new IdentityHashMap<>());
        Iterator<String> beanNames = beanFactory.getBeanNamesIterator();
        while (beanNames.hasNext()) {
            String beanName = beanNames.next();
            Class<?> declaredType = beanFactory.getType(beanName, false);
            if (declaredType != null && hasMessageMapping(declaredType)) {
                Object bean = beanFactory.getBean(beanName);
                if (registeredBeans.add(bean)) {
                    registerBean(bean);
                }
                continue;
            }
            Object bean = beanFactory.containsSingleton(beanName) && !beanFactory.isFactoryBean(beanName)
                    ? beanFactory.getSingleton(beanName)
                    : null;
            if (bean != null && hasMessageMapping(AopUtils.getTargetClass(bean)) && registeredBeans.add(bean)) {
                registerBean(bean);
            }
        }
    }

    /**
     * 扫描单个 Spring Bean 并注册其注解方法。
     *
     * @param bean 目标 Bean
     */
    public void registerBean(Object bean) {
        Objects.requireNonNull(bean, "bean must not be null");
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Map<Method, WsMessageMapping> methods = MethodIntrospector.selectMethods(
                targetClass,
                (MethodIntrospector.MetadataLookup<WsMessageMapping>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, WsMessageMapping.class));
        for (Map.Entry<Method, WsMessageMapping> entry : methods.entrySet()) {
            Method invocableMethod = AopUtils.selectInvocableMethod(entry.getKey(), bean.getClass());
            validateMethod(invocableMethod);
            WsMessageMapping mapping = entry.getValue();
            WsAuth authorization = AnnotatedElementUtils.findMergedAnnotation(entry.getKey(), WsAuth.class);
            router.register(mapping.value(), new AnnotatedWsMessageHandler(
                    bean, invocableMethod, mapping.value(), authorization));
        }
    }

    /**
     * 校验注解处理方法的稳定签名。
     *
     * @param method 注解方法
     */
    private void validateMethod(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean valid = Modifier.isPublic(method.getModifiers())
                && method.getReturnType() == Void.TYPE
                && parameterTypes.length == 2
                && parameterTypes[0] == WsSession.class
                && parameterTypes[1] == WsMessage.class;
        if (!valid) {
            throw WsException.configurationInvalid(
                    "@WsMessageMapping 方法必须是 public void method(WsSession, WsMessage)");
        }
    }

    /**
     * 判断 Bean 类型是否声明了消息映射方法，避免为扫描而提前实例化无关 Bean。
     *
     * @param beanType Bean 类型
     * @return 存在消息映射方法时返回 {@code true}
     */
    private boolean hasMessageMapping(Class<?> beanType) {
        return !MethodIntrospector.selectMethods(
                beanType,
                (MethodIntrospector.MetadataLookup<WsMessageMapping>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, WsMessageMapping.class)).isEmpty();
    }
}
