package com.github.leyland.letool.letool.tool.util;

import com.github.leyland.letool.letool.tool.test.MyInterface;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @ClassName <h2>ClassScannerUtil</h2>
 * @Description
 * @Author rungo
 * @Date 12/12/2024
 * @Version 1.0
 **/
public class ClassScannerUtil {

    public static List<Class<?>> findInstantiableImplementations(Class<?> interfaceClass, String packageName) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String packagePath = packageName.replace('.', '/');
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            File directory = new File(java.net.URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8));
            if (directory.exists()) {
                classes.addAll(findClassesInDirectory(interfaceClass, directory, packageName));
            }
        }
        return classes;
    }

    private static List<Class<?>> findClassesInDirectory(Class<?> interfaceClass, File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        List<Future<List<Class<?>>>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                // 递归处理子目录
                futures.add(executorService.submit(() -> findClassesInDirectory(interfaceClass, file, packageName + "." + file.getName())));
            } else if (file.getName().endsWith(".class")) {
                // 获取类全限定名
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());

                    // 判断是否符合条件：实现了接口，并且可实例化
                    if (interfaceClass.isAssignableFrom(clazz)        // 实现了接口
                            && !clazz.equals(interfaceClass)          // 不是接口本身
                            && !clazz.isInterface()                   // 不是接口
                            && !Modifier.isAbstract(clazz.getModifiers()) // 不是抽象类
                    ) {
                        classes.add(clazz);
                    }
                } catch (Throwable ex) {
                    // 避免类加载副作用导致扫描中断
                    System.err.println("Failed to load class: " + className + " due to: " + ex.getMessage());
                }
            }
        }

        // 合并多线程结果
        for (Future<List<Class<?>>> future : futures) {
            try {
                classes.addAll(future.get());
            } catch (Exception e) {
                System.err.println("Error during parallel scanning: " + e.getMessage());
            }
        }
        executorService.shutdown();

        return classes;
    }

    public static void main(String[] args) {
        try {
            // 示例：扫描 com.example 包下所有实现了 MyInterface 的可实例化类
            List<Class<?>> implementations = findInstantiableImplementations(MyInterface.class, "com.github.leyland.letool.tool.util.test");
            for (Class<?> clazz : implementations) {
                System.out.println("Found class: " + clazz.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
