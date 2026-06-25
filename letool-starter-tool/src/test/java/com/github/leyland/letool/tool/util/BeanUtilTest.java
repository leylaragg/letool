package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BeanUtilTest {

    static class Source {
        private String name;
        private int age;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    static class Target {
        private String name;
        private int age;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @Test
    void copy() {
        Source src = new Source();
        src.setName("test");
        src.setAge(10);
        Target tgt = BeanUtil.copy(src, Target.class);
        assertEquals("test", tgt.getName());
        assertEquals(10, tgt.getAge());
    }

    @Test
    void copyList() {
        List<Source> srcList = new ArrayList<>();
        Source s1 = new Source(); s1.setName("a"); s1.setAge(1); srcList.add(s1);
        Source s2 = new Source(); s2.setName("b"); s2.setAge(2); srcList.add(s2);
        List<Target> targets = BeanUtil.copyList(srcList, Target.class);
        assertEquals(2, targets.size());
        assertEquals("a", targets.get(0).getName());
    }

    @Test
    void copyListFast() {
        List<Source> srcList = new ArrayList<>();
        Source s1 = new Source(); s1.setName("x"); s1.setAge(99); srcList.add(s1);
        List<Target> targets = BeanUtil.copyListFast(srcList, Target.class);
        assertEquals(1, targets.size());
        assertEquals("x", targets.get(0).getName());
    }

    @Test
    void newInstance() {
        Target t = BeanUtil.newInstance(Target.class);
        assertNotNull(t);
    }
}
