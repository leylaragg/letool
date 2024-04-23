package com.github.leyland.letool.demo.spring.mvc.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName <h2>MapToObject</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class MapToObject {

    public static void main(String[] args) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");
        data.put("age", 30);
        data.put("address", "123 Main St");

        // 构建一个包含对象的Map
        Map<String, Object> nestedData = new HashMap<>();
        nestedData.put("city", "New York");
        nestedData.put("country", "USA");
        data.put("location", nestedData);

        // 构建一个包含List对象的Map
        List<String> hobbies = new ArrayList<>();
        hobbies.add("Reading");
        hobbies.add("Gardening");
        data.put("hobbies", hobbies);

        // 创建目标对象
        Person person = new Person();

        // 将Map的值赋给目标对象
        mapToObj(data, person);

        // 输出结果
        System.out.println(person.getName()); // John
        System.out.println(person.getAge()); // 30
        System.out.println(person.getAddress()); // 123 Main St
        System.out.println(person.getLocation().getCity()); // New York
        System.out.println(person.getLocation().getCountry()); // USA
        System.out.println(person.getHobbies()); // [Reading, Gardening]
    }

    public static void mapToObj(Map<String, Object> data, Object obj) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();
            try {
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);

                // 如果字段是对象类型，则递归调用mapToObj方法
                if (field.getType().equals(List.class) && fieldValue instanceof List) {
                    List<Object> list = new ArrayList<>();
                    mapToList((List<Object>) fieldValue, list);
                    field.set(obj, list);
                } else if (field.getType().equals(Map.class) && fieldValue instanceof Map) {
                    Object fieldObj = field.getType().newInstance();
                    mapToObj((Map<String, Object>) fieldValue, fieldObj);
                    field.set(obj, fieldObj);
                } else {
                    // 如果字段值为null，则为该字段实例化一个对象
                    if (fieldValue != null) {
                        field.set(obj, fieldValue);
                    } else {
                        field.set(obj, field.getType().newInstance());
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException | InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    public static void mapToList(List<Object> listData, List<Object> list) {
        for (Object item : listData) {
            if (item instanceof Map) {
                try {
                    Object listItem = listData.get(0).getClass().newInstance();
                    mapToObj((Map<String, Object>) item, listItem);
                    list.add(listItem);
                } catch (InstantiationException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            } else {
                list.add(item);
            }
        }
    }

    static class Person {
        private String name;
        private int age;
        private String address;
        private Location location;
        private List<String> hobbies;

        // Getters and setters

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

        public List<String> getHobbies() {
            return hobbies;
        }

        public void setHobbies(List<String> hobbies) {
            this.hobbies = hobbies;
        }
    }

    static class Location {
        private String city;
        private String country;

        // Getters and setters

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }
}
