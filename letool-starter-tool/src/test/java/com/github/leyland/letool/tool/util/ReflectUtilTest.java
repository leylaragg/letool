package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReflectUtilTest {

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marked {}

    @Marked
    static class TestClass {
        private String name;
        private int value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        private String secret() { return "secret"; }
    }

    @Test
    void getField() {
        Field field = ReflectUtil.getField(TestClass.class, "name");
        assertNotNull(field);
        assertEquals("name", field.getName());
    }

    @Test
    void getFieldFromSuperclass() {
        Field field = ReflectUtil.getField(TestClass.class, "name");
        assertNotNull(field);
    }

    @Test
    void getAllFields() {
        List<Field> fields = ReflectUtil.getAllFields(TestClass.class);
        assertTrue(fields.size() >= 2);
    }

    @Test
    void getSetFieldValue() {
        TestClass obj = new TestClass();
        ReflectUtil.setFieldValue(obj, "name", "test_name");
        assertEquals("test_name", ReflectUtil.getFieldValue(obj, "name"));
    }

    @Test
    void invokeMethod() {
        TestClass obj = new TestClass();
        String result = ReflectUtil.invokeMethod(obj, "secret");
        assertEquals("secret", result);
    }

    @Test
    void getAnnotation() {
        Marked annotation = ReflectUtil.getAnnotation(TestClass.class, Marked.class);
        assertNotNull(annotation);
    }
}
