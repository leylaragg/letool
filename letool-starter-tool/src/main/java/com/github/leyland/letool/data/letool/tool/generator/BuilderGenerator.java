package com.github.leyland.letool.data.letool.tool.generator;

import java.lang.reflect.Field;

/**
 * @ClassName <h2>BuilderGenerator</h2>
 * @Description TODO builder 生产器
 * @Author Rungo
 * @Version 1.0
 **/
public class BuilderGenerator {

    // 生成Builder模式的代码
    public static String generateBuilder(Class<?> clazz) {
        StringBuilder builderCode = new StringBuilder();
        String className = clazz.getSimpleName();

        builderCode.append("public static class Builder {\n");

        // 遍历类的所有字段
        for (Field field : clazz.getDeclaredFields()) {
            String fieldName = field.getName();
            String fieldType = field.getType().getSimpleName();

            // 生成Builder的setter方法
            builderCode.append("    private ").append(fieldType).append(" ").append(fieldName).append(";\n");
            builderCode.append("    public Builder ").append(fieldName).append("(").append(fieldType).append(" ").append(fieldName).append(") {\n");
            builderCode.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
            builderCode.append("        return this;\n");
            builderCode.append("    }\n");
        }

        // 生成构建方法
        builderCode.append("\n");
        builderCode.append("    public ").append(className).append(" build() {\n");
        builderCode.append("        return new ").append(className).append("(");

        // 生成构建方法的参数列表
        for (Field field : clazz.getDeclaredFields()) {
            builderCode.append(field.getName()).append(", ");
        }
        // 删除最后多余的逗号和空格
        builderCode.delete(builderCode.length() - 2, builderCode.length());
        builderCode.append(");\n");
        builderCode.append("    }\n");

        builderCode.append("}\n");

        return builderCode.toString();
    }

    public static void main(String[] args) {
        // 测试生成Builder模式的代码
        String builderCode = generateBuilder(MyClass.class);
        System.out.println(builderCode);
    }

    // 示例类
    public static class MyClass {
        private String field1;
        private int field2;

        public MyClass(String field1, int field2) {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

}
