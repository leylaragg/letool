package com.github.leyland.letool.tool.mapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高级对象映射器演示
 * 展示性能优化、批量映射、数据脱敏、Map支持等高级功能
 *
 * @author leyland
 * @date 2025-01-08
 */
public class AdvancedObjectMapperDemo {

    public static void main(String[] args) {
        // ========== 性能优化演示 ==========
        performanceDemo();

        // ========== 批量映射演示 ==========
        batchMappingDemo();

        // ========== 数据脱敏演示 ==========
        sensitiveDemo();

        // ========== Map数据源演示 ==========
        mapSourceDemo();

        // ========== 映射监听器演示 ==========
        listenerDemo();
    }

    /**
     * 性能优化演示：展示缓存带来的性能提升
     */
    private static void performanceDemo() {
        System.out.println("========== 性能优化演示 ==========");

        // 创建测试数据
        ObjectMapperDemo.UserEntity user = new ObjectMapperDemo.UserEntity();
        user.setId(1L);
        user.setName("张三");
        user.setAge(25);

        ObjectMapperDemo.UserVO vo = new ObjectMapperDemo.UserVO();

        // 预热
        for (int i = 0; i < 100; i++) {
            ObjectMapper.map(vo, user);
        }

        // 测试性能
        int iterations = 10000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            ObjectMapper.map(vo, user);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("映射 " + iterations + " 次耗时: " + duration + "ms");
        System.out.println("平均每次: " + (duration * 1000.0 / iterations) + "μs");
        System.out.println();
    }

    /**
     * 批量映射演示
     */
    private static void batchMappingDemo() {
        System.out.println("========== 批量映射演示 ==========");

        // 准备数据
        List<ObjectMapperDemo.UserEntity> userList = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ObjectMapperDemo.UserEntity user = new ObjectMapperDemo.UserEntity();
            user.setId((long) i);
            user.setName("用户" + i);
            user.setAge(20 + i);
            userList.add(user);
        }

        // 额外的固定源数据
        ObjectMapperDemo.OrderEntity order = new ObjectMapperDemo.OrderEntity();
        order.setOrderNo("BATCH-ORDER-001");
        order.setTotalAmount(new java.math.BigDecimal("1000.00"));

        // 批量映射
        List<ObjectMapperDemo.UserDetailVO> detailVOList = ObjectMapper.mapList(userList, ObjectMapperDemo.UserDetailVO.class, order);

        // 输出结果
        System.out.println("批量映射结果数量: " + detailVOList.size());
        for (ObjectMapperDemo.UserDetailVO detailVO : detailVOList) {
            System.out.println(detailVO);
        }
        System.out.println();
    }

    /**
     * 数据脱敏演示
     */
    private static void sensitiveDemo() {
        System.out.println("========== 数据脱敏演示 ==========");

        // 创建包含敏感信息的实体
        SensitiveDataEntity entity = new SensitiveDataEntity();
        entity.setName("张三");
        entity.setIdCard("123456199001011234");
        entity.setMobilePhone("13800138000");
        entity.setEmail("zhangsan@example.com");
        entity.setBankCard("6222021234567890123");
        entity.setAddress("北京市朝阳区xxx街道xxx号");
        entity.setPassword("MyPassword123");

        // 映射到VO（自动脱敏）
        SensitiveDataVO vo = new SensitiveDataVO();
        ObjectMapper.map(vo, entity);

        // 输出结果
        System.out.println("原始数据:");
        System.out.println("  姓名: " + entity.getName());
        System.out.println("  身份证: " + entity.getIdCard());
        System.out.println("  手机号: " + entity.getMobilePhone());
        System.out.println("  邮箱: " + entity.getEmail());
        System.out.println("  银行卡: " + entity.getBankCard());
        System.out.println("  地址: " + entity.getAddress());
        System.out.println("  密码: " + entity.getPassword());

        System.out.println("\n脱敏后数据:");
        System.out.println("  姓名: " + vo.getName());
        System.out.println("  身份证: " + vo.getIdCard());
        System.out.println("  手机号: " + vo.getMobilePhone());
        System.out.println("  邮箱: " + vo.getEmail());
        System.out.println("  银行卡: " + vo.getBankCard());
        System.out.println("  地址: " + vo.getAddress());
        System.out.println("  密码: " + vo.getPassword());
        System.out.println();
    }

    /**
     * Map数据源演示
     */
    private static void mapSourceDemo() {
        System.out.println("========== Map数据源演示 ==========");

        // 创建Map数据源
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 100L);
        userMap.put("name", "Map用户");
        userMap.put("age", 30);

        // 嵌套Map
        Map<String, Object> profileMap = new HashMap<>();
        profileMap.put("nickname", "Map昵称");

        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("city", "上海市");
        addressMap.put("district", "浦东新区");
        profileMap.put("address", addressMap);
        userMap.put("profile", profileMap);

        // 映射到VO
        ObjectMapperDemo.UserLocationVO vo = new ObjectMapperDemo.UserLocationVO();
        ObjectMapper.map(vo, userMap);

        // 输出结果
        System.out.println("从Map映射结果: " + vo);
        System.out.println();
    }

    /**
     * 映射监听器演示
     */
    private static void listenerDemo() {
        System.out.println("========== 映射监听器演示 ==========");

        // 创建监听器
        MapperListener listener = new MapperListener() {
            private final AtomicInteger successCount = new AtomicInteger(0);
            private final AtomicInteger errorCount = new AtomicInteger(0);

            @Override
            public void beforeMapping(Object target, Object[] sources) {
                System.out.println("开始映射: " + target.getClass().getSimpleName());
            }

            @Override
            public void afterMapping(Object target, Object[] sources) {
                System.out.println("映射完成，成功: " + successCount.get() + ", 失败: " + errorCount.get());
            }

            @Override
            public void onFieldMappingError(Object target, java.lang.reflect.Field targetField, Object source, Exception exception) {
                errorCount.incrementAndGet();
                System.out.println("  字段映射失败: " + targetField.getName() + ", 原因: " + exception.getMessage());
            }

            // 成功计数在 setFieldValue 中通过反射调用
        };

        // 创建数据
        ObjectMapperDemo.UserEntity user = new ObjectMapperDemo.UserEntity();
        user.setId(1L);
        user.setName("监听器测试");
        user.setAge(28);

        ObjectMapperDemo.UserVO vo = new ObjectMapperDemo.UserVO();

        // 使用监听器进行映射
        ObjectMapper.map(vo, listener, user);

        // 输出结果
        System.out.println("最终结果: " + vo);
        System.out.println();
    }

    // ========== 测试用的实体类和VO类 ==========

    /**
     * 敏感数据实体
     */
    static class SensitiveDataEntity {
        private String name;
        private String idCard;
        private String mobilePhone;
        private String email;
        private String bankCard;
        private String address;
        private String password;

        // Getter and Setter
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getMobilePhone() { return mobilePhone; }
        public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getBankCard() { return bankCard; }
        public void setBankCard(String bankCard) { this.bankCard = bankCard; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /**
     * 敏感数据VO（带脱敏注解）
     */
    static class SensitiveDataVO {
        @MapFrom
        @Sensitive(SensitiveType.CHINESE_NAME)
        private String name;

        @MapFrom
        @Sensitive(SensitiveType.ID_CARD)
        private String idCard;

        @MapFrom
        @Sensitive(SensitiveType.MOBILE_PHONE)
        private String mobilePhone;

        @MapFrom
        @Sensitive(SensitiveType.EMAIL)
        private String email;

        @MapFrom
        @Sensitive(SensitiveType.BANK_CARD)
        private String bankCard;

        @MapFrom
        @Sensitive(SensitiveType.ADDRESS)
        private String address;

        @MapFrom
        @Sensitive(SensitiveType.PASSWORD)
        private String password;

        // Getter and Setter
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getMobilePhone() { return mobilePhone; }
        public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getBankCard() { return bankCard; }
        public void setBankCard(String bankCard) { this.bankCard = bankCard; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        @Override
        public String toString() {
            return "SensitiveDataVO{" +
                    "name='" + name + '\'' +
                    ", idCard='" + idCard + '\'' +
                    ", mobilePhone='" + mobilePhone + '\'' +
                    ", email='" + email + '\'' +
                    ", bankCard='" + bankCard + '\'' +
                    ", address='" + address + '\'' +
                    ", password='" + password + '\'' +
                    '}';
        }
    }
}
