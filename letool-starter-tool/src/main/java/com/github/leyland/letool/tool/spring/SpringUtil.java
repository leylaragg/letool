package com.github.leyland.letool.tool.spring;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * Spring 容器工具——在非 Spring Bean 中获取 Bean 实例和配置属性.
 *
 * <h3>设计说明</h3>
 * <p>通过实现 {@link ApplicationContextAware} 接口持有 ApplicationContext 的静态引用，
 * 使非 Spring 管理的类（如工具类、POJO）也能获取 Bean 和配置.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 获取 Bean
 * UserService service = SpringUtil.getBean(UserService.class);
 * UserService service = SpringUtil.getBean("userService", UserService.class);
 *
 * // 获取配置
 * String appName = SpringUtil.getProperty("spring.application.name");
 * int port = SpringUtil.getProperty("server.port", Integer.class);
 *
 * // 获取所有实现某接口的 Bean
 * Map<String, DataHandler> handlers = SpringUtil.getBeansOfType(DataHandler.class);
 *
 * // 判断 Bean 是否存在
 * if (SpringUtil.containsBean("dataSource")) { ... }
 *
 * // 获取当前激活的 Profile
 * String profile = SpringUtil.getActiveProfile();  // → "dev" or "prod"
 * }</pre>
 *
 * <p>注意：必须在 Spring 容器中注册（由 {@code @ComponentScan} 扫描到），
 * 静态方法在容器初始化前调用会 NPE.</p>
 */
public class SpringUtil implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        SpringUtil.context = applicationContext;
    }

    /**
     * 获取 ApplicationContext 实例.
     *
     * @return Spring 应用上下文，容器未初始化返回 {@code null}
     */
    public static ApplicationContext getContext() {
        return context;
    }

    /**
     * 按类型获取 Bean.
     *
     * @param clazz Bean 类型
     * @param <T>   泛型
     * @return Bean 实例
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException 如果不存在
     */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    /**
     * 按名称和类型获取 Bean.
     *
     * @param name  Bean 名称
     * @param clazz Bean 类型
     * @param <T>   泛型
     * @return Bean 实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return context.getBean(name, clazz);
    }

    /**
     * 按名称获取 Bean（不检查类型）.
     *
     * @param name Bean 名称
     * @param <T>  泛型（调用方自行保证类型安全）
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) context.getBean(name);
    }

    /**
     * 获取所有实现指定类型的 Bean.
     *
     * @param clazz 目标类型（通常是接口）
     * @param <T>   泛型
     * @return Bean 名称 → Bean 实例的 Map（可能为空）
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return context.getBeansOfType(clazz);
    }

    /**
     * 获取配置属性（String 类型）.
     *
     * @param key 配置键
     * @return 配置值，不存在返回 {@code null}
     */
    public static String getProperty(String key) {
        return context.getEnvironment().getProperty(key);
    }

    /**
     * 获取配置属性（任意类型，自动转换）.
     *
     * @param key        配置键
     * @param targetType 目标类型
     * @param <T>        泛型
     * @return 配置值，不存在返回 {@code null}
     */
    public static <T> T getProperty(String key, Class<T> targetType) {
        return context.getEnvironment().getProperty(key, targetType);
    }

    /**
     * 获取配置属性（带默认值）.
     *
     * @param key          配置键
     * @param defaultValue 默认值（不存在时返回）
     * @return 配置值或默认值
     */
    public static String getProperty(String key, String defaultValue) {
        return context.getEnvironment().getProperty(key, defaultValue);
    }

    /**
     * 判断容器中是否存在指定名称的 Bean.
     *
     * @param name Bean 名称
     * @return {@code true} 如果存在
     */
    public static boolean containsBean(String name) {
        return context.containsBean(name);
    }

    /**
     * 判断指定名称的 Bean 是否为 Singleton 作用域.
     *
     * @param name Bean 名称
     * @return {@code true} 如果为 Singleton
     */
    public static boolean isSingleton(String name) {
        return context.isSingleton(name);
    }

    /**
     * 获取当前激活的 Spring Profile 名称.
     *
     * <p>多个 Profile 时返回第一个激活的；没有激活的返回 {@code "default"}.</p>
     *
     * @return Profile 名称
     */
    public static String getActiveProfile() {
        String[] profiles = context.getEnvironment().getActiveProfiles();
        return profiles.length > 0 ? profiles[0] : "default";
    }
}
