package com.github.leyland.letool.job.quartz;

import com.github.leyland.letool.job.annotation.LetoolJob;
import com.github.leyland.letool.job.core.JobContext;
import com.github.leyland.letool.job.core.JobDefinition;
import com.github.leyland.letool.job.core.JobHandler;
import com.github.leyland.letool.job.core.JobHandlerRegistry;
import com.github.leyland.letool.job.exception.JobErrorCode;
import com.github.leyland.letool.job.exception.JobException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

/**
 * 扫描 {@link LetoolJob} Spring Bean 并注册处理器与调度定义。
 */
public class LetoolJobRegistrar implements SmartInitializingSingleton {

    private final ListableBeanFactory beanFactory;
    private final JobHandlerRegistry handlerRegistry;
    private final RegistrationSink registrationSink;

    /**
     * 创建任务注解注册器。
     *
     * @param beanFactory Spring Bean 查询入口
     * @param handlerRegistry 当前节点处理器注册表
     * @param registrationSink 任务定义注册回调
     */
    public LetoolJobRegistrar(
            ListableBeanFactory beanFactory,
            JobHandlerRegistry handlerRegistry,
            RegistrationSink registrationSink) {
        this.beanFactory = beanFactory;
        this.handlerRegistry = handlerRegistry;
        this.registrationSink = registrationSink;
    }

    /**
     * 在所有单例初始化完成后按 Bean 名稳定顺序注册任务。
     */
    @Override
    public void afterSingletonsInstantiated() {
        String[] beanNames = beanFactory.getBeanNamesForAnnotation(LetoolJob.class);
        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            registerBean(beanName);
        }
    }

    private void registerBean(String beanName) {
        Object bean = beanFactory.getBean(beanName);
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        LetoolJob annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, LetoolJob.class);
        if (annotation == null) {
            return;
        }
        Method handlerMethod = findHandlerMethod(targetClass, annotation.name());
        Method invocableMethod = AopUtils.selectInvocableMethod(handlerMethod, bean.getClass());
        JobHandler handler = context -> invoke(bean, invocableMethod, context);
        JobDefinition definition = toDefinition(annotation);

        handlerRegistry.register(definition.getJobName(), handler);
        try {
            registrationSink.register(definition, beanName);
        } catch (RuntimeException exception) {
            handlerRegistry.unregister(definition.getJobName());
            throw exception;
        }
    }

    private Method findHandlerMethod(Class<?> targetClass, String jobName) {
        Map<Method, com.github.leyland.letool.job.annotation.JobHandler> methods =
                MethodIntrospector.selectMethods(targetClass,
                        (MethodIntrospector.MetadataLookup<com.github.leyland.letool.job.annotation.JobHandler>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(
                                method, com.github.leyland.letool.job.annotation.JobHandler.class));
        if (methods.size() != 1) {
            throw invalidHandler(jobName, "任务类必须且只能声明一个 @JobHandler 方法");
        }
        Method method = methods.keySet().iterator().next();
        if (!Modifier.isPublic(method.getModifiers()) || method.getReturnType() != Void.TYPE) {
            throw invalidHandler(jobName, "@JobHandler 必须是公开 void 方法");
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 1
                || parameterTypes.length == 1 && parameterTypes[0] != JobContext.class) {
            throw invalidHandler(jobName, "@JobHandler 只能无参数或接收唯一 JobContext 参数");
        }
        return method;
    }

    private void invoke(Object bean, Method method, JobContext context) throws Exception {
        try {
            if (method.getParameterCount() == 0) {
                method.invoke(bean);
            } else {
                method.invoke(bean, context);
            }
        } catch (InvocationTargetException exception) {
            Throwable target = exception.getTargetException();
            if (target instanceof Exception checked) {
                throw checked;
            }
            if (target instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(target);
        } catch (ReflectiveOperationException exception) {
            throw new JobException(JobErrorCode.INVALID_HANDLER, context == null ? null : context.getJobName(),
                    exception, method.toGenericString());
        }
    }

    private JobDefinition toDefinition(LetoolJob annotation) {
        try {
            return JobDefinition.builder()
                    .jobName(annotation.name())
                    .cron(annotation.cron())
                    .zone(annotation.zone())
                    .description(annotation.description())
                    .shardTotal(annotation.shardTotal())
                    .maxRetries(annotation.maxRetries())
                    .backoffMs(annotation.backoffMs())
                    .backoffMultiplier(annotation.backoffMultiplier())
                    .maxBackoffMs(annotation.maxBackoffMs())
                    .concurrent(annotation.concurrent())
                    .misfirePolicy(annotation.misfirePolicy())
                    .requestRecovery(annotation.requestRecovery())
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new JobException(
                    JobErrorCode.INVALID_DEFINITION,
                    annotation.name(),
                    exception,
                    exception.getMessage());
        }
    }

    private JobException invalidHandler(String jobName, String message) {
        return new JobException(JobErrorCode.INVALID_HANDLER, jobName, message);
    }

    /**
     * 将扫描结果交给调度门面的窄接口。
     */
    @FunctionalInterface
    public interface RegistrationSink {

        /**
         * 注册一个任务定义。
         *
         * @param definition 任务定义
         * @param handlerBeanName 处理器 Spring Bean 名称
         */
        void register(JobDefinition definition, String handlerBeanName);
    }
}
