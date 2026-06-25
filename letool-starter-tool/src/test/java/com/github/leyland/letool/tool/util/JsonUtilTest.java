package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    static class User {
        private String name;
        private int age;
        public User() {}
        public User(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @Test
    void toJsonString() {
        User user = new User("张三", 25);
        String json = JsonUtil.toJsonString(user);
        assertNotNull(json);
        assertTrue(json.contains("张三"));
    }

    @Test
    void parseObject() {
        String json = "{\"name\":\"李四\",\"age\":30}";
        User user = JsonUtil.parseObject(json, User.class);
        assertNotNull(user);
        assertEquals("李四", user.getName());
        assertEquals(30, user.getAge());
    }

    @Test
    void parseArray() {
        String json = "[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]";
        List<User> users = JsonUtil.parseArray(json, User.class);
        assertEquals(2, users.size());
    }

    @Test
    void toMap() {
        User user = new User("test", 30);
        Map<String, Object> map = JsonUtil.toMap(user);
        assertEquals("test", map.get("name"));
        assertEquals(30, map.get("age"));
    }
}
