package mapper;

import com.github.leyland.letool.data.desensitize.Sensitive;
import com.github.leyland.letool.data.desensitize.SensitiveType;
import com.github.leyland.letool.data.mapper.ObjectMapperUtil;
import com.github.leyland.letool.data.mapper.annotation.MapField;
import com.github.leyland.letool.data.mapper.context.MappingConfig;
import com.github.leyland.letool.data.mapper.handler.FieldMappingHandler;
import com.github.leyland.letool.data.mapper.holder.HandlerHolder;
import com.github.leyland.letool.data.mapper.context.MappingContext;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 对象映射器演示
 * 展示如何使用 ObjectMapper 进行对象映射
 *
 * @author leyland
 * @date 2025-01-08
 */
public class ObjectMapperDemo {

    public static void main(String[] args) {
        // ========== 基础映射示例 ==========
        basicMappingExample();

        // ========== 多源对象映射示例 ==========
        multiSourceMappingExample();

        // ========== 脱敏映射示例 ==========
        sensitiveMappingExample();

        // ========== 格式化映射示例 ==========
        formatMappingExample();

        // ========== 自定义配置示例 ==========
        customConfigExample();

        // ========== 批量映射示例 ==========
        batchMappingExample();

        // ========== 自定义处理器示例 ==========
        customHandlerExample();
    }

    /**
     * 基础映射示例
     */
    private static void basicMappingExample() {
        System.out.println("========== 基础映射示例 ==========");

        // 创建源对象
        ObjectMapperDemo.UserEntity user = new ObjectMapperDemo.UserEntity();
        user.setId(1L);
        user.setName("张三");
        user.setAge(25);
        user.setEmail("zhangsan@example.com");

        // 创建目标VO
        ObjectMapperDemo.UserVO userVO = new ObjectMapperDemo.UserVO();

        // 执行映射
        ObjectMapperUtil.map(userVO, user);

        // 输出结果
        System.out.println("UserVO: " + userVO);
        System.out.println();
    }

    /**
     * 多源对象映射示例
     */
    private static void multiSourceMappingExample() {
        System.out.println("========== 多源对象映射示例 ==========");

        // 创建源对象
        ObjectMapperDemo.UserEntity user = new ObjectMapperDemo.UserEntity();
        user.setId(1L);
        user.setName("李四");
        user.setAge(30);

        ObjectMapperDemo.OrderEntity order = new ObjectMapperDemo.OrderEntity();
        order.setOrderNo("ORDER-2025-001");
        order.setTotalAmount(new BigDecimal("1299.99"));
        order.setCreateTime(new Date());

        // 创建目标VO
        ObjectMapperDemo.OrderDetailVO orderDetailVO = new ObjectMapperDemo.OrderDetailVO();

        // 执行映射
        ObjectMapperUtil.map(orderDetailVO, user, order);

        // 输出结果
        System.out.println("OrderDetailVO: " + orderDetailVO);
        System.out.println();
    }

    /**
     * 脱敏映射示例
     */
    private static void sensitiveMappingExample() {
        System.out.println("========== 脱敏映射示例 ==========");

        // 创建源对象
        ObjectMapperDemo.UserProfileEntity profile = new ObjectMapperDemo.UserProfileEntity();
        profile.setId(1L);
        profile.setName("王五");
        profile.setIdCard("430102199001011234");
        profile.setPhone("13800138000");
        profile.setEmail("wangwu@qq.com");

        // 创建目标VO
        ObjectMapperDemo.UserProfileVO profileVO = new ObjectMapperDemo.UserProfileVO();

        // 执行映射（自动应用脱敏）
        ObjectMapperUtil.map(profileVO, profile);

        // 输出结果
        System.out.println("UserProfileVO: " + profileVO);
        System.out.println();
    }

    /**
     * 格式化映射示例
     */
    private static void formatMappingExample() {
        System.out.println("========== 格式化映射示例 ==========");

        // 创建源对象
        ObjectMapperDemo.ProductEntity product = new ObjectMapperDemo.ProductEntity();
        product.setProductName("高性能笔记本");
        product.setPrice(new BigDecimal("5999.99"));
        product.setCreateTime(new Date());

        // 创建目标VO
        ObjectMapperDemo.ProductVO productVO = new ObjectMapperDemo.ProductVO();

        // 执行映射（自动应用格式化）
        ObjectMapperUtil.map(productVO, product);

        // 输出结果
        System.out.println("ProductVO: " + productVO);
        System.out.println();
    }

    /**
     * 自定义配置示例
     */
    private static void customConfigExample() {
        System.out.println("========== 自定义配置示例 ==========");

        // 创建自定义配置
        MappingConfig config = new MappingConfig()
                .setDefaultDateFormat("yyyy年MM月dd日 HH:mm:ss")
                .setIgnoreNull(true)
                .setEnableHandlerChain(true);

        // 创建源对象
        ObjectMapperDemo.ProductEntity product = new ObjectMapperDemo.ProductEntity();
        product.setProductName("测试商品");
        product.setPrice(new BigDecimal("199.99"));
        product.setCreateTime(new Date());

        // 创建目标VO
        ObjectMapperDemo.ProductVO productVO = new ObjectMapperDemo.ProductVO();

        // 执行映射（使用自定义配置）
        ObjectMapperUtil.map(productVO, config, product);

        // 输出结果
        System.out.println("ProductVO (自定义配置): " + productVO);
        System.out.println();
    }

    /**
     * 批量映射示例
     */
    private static void batchMappingExample() {
        System.out.println("========== 批量映射示例 ==========");

        // 创建源对象列表
        List<ObjectMapperDemo.UserEntity> users = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ObjectMapperDemo.UserEntity user = new ObjectMapperDemo.UserEntity();
            user.setId((long) i);
            user.setName("用户" + i);
            user.setAge(20 + i);
            user.setEmail("user" + i + "@example.com");
            users.add(user);
        }

        // 执行批量映射
        List<ObjectMapperDemo.UserVO> userVOs = ObjectMapperUtil.mapList(users, ObjectMapperDemo.UserVO.class);

        // 输出结果
        for (ObjectMapperDemo.UserVO userVO : userVOs) {
            System.out.println(userVO);
        }
        System.out.println();
    }

    /**
     * 自定义处理器示例
     */
    private static void customHandlerExample() {
        System.out.println("========== 自定义处理器示例 ==========");

        try {
            // 注册自定义处理器
            HandlerHolder.register(new ObjectMapperDemo.CustomFieldHandler());

            // 创建源对象
            ObjectMapperDemo.UserEntity user = new ObjectMapperDemo.UserEntity();
            user.setId(1L);
            user.setName("张三");
            user.setAge(25);
            user.setEmail("zhangsan@example.com");

            // 创建目标VO
            ObjectMapperDemo.UserVO userVO = new ObjectMapperDemo.UserVO();

            // 执行映射
            ObjectMapperUtil.map(userVO, user);

            // 输出结果
            System.out.println("UserVO (自定义处理器): " + userVO);
            System.out.println();
        } finally {
            // 清理
            try {
                HandlerHolder.unregister(ObjectMapperDemo.CustomFieldHandler.class);
            } catch (Exception e) {
                // 忽略清理失败
            }
        }
    }

    // ========== 测试用的实体类 ==========

    public static class UserEntity {
        private Long id;
        private String name;
        private Integer age;
        private String email;

        // Getter and Setter
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class UserProfileEntity {
        private Long id;
        private String name;
        private String idCard;
        private String phone;
        private String email;

        // Getter and Setter
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ProductEntity {
        private String productName;
        private BigDecimal price;
        private Date createTime;

        // Getter and Setter
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
    }

    public static class OrderEntity {
        private String orderNo;
        private BigDecimal totalAmount;
        private Date createTime;

        // Getter and Setter
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
    }

    // ========== 测试用的VO类 ==========

    public static class UserVO {
        @MapField
        private Long id;

        @MapField
        private String name;

        @MapField
        private Integer age;

        @MapField
        private String email;

        // Getter and Setter
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @Override
        public String toString() {
            return "UserVO{id=" + id + ", name='" + name + "', age=" + age + ", email='" + email + "'}";
        }
    }

    public static class UserProfileVO {
        @MapField
        private Long id;

        @MapField
        private String name;

        @MapField
        @Sensitive(SensitiveType.ID_CARD)
        private String idCard;

        @MapField
        @Sensitive(SensitiveType.MOBILE_PHONE)
        private String phone;

        @MapField
        @Sensitive(SensitiveType.EMAIL)
        private String email;

        // Getter and Setter
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        @Override
        public String toString() {
            return "UserProfileVO{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", idCard='" + idCard + '\'' +
                    ", phone='" + phone + '\'' +
                    ", email='" + email + '\'' +
                    '}';
        }
    }

    public static class ProductVO {
        @MapField(sourcePath = "productName")
        private String name;

        @MapField(sourcePath = "price", format = "¥#,##0.00")
        private String priceDisplay;

        @MapField(sourcePath = "createTime", format = "yyyy-MM-dd HH:mm:ss")
        private String createTimeDisplay;

        // Getter and Setter
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPriceDisplay() { return priceDisplay; }
        public void setPriceDisplay(String priceDisplay) { this.priceDisplay = priceDisplay; }
        public String getCreateTimeDisplay() { return createTimeDisplay; }
        public void setCreateTimeDisplay(String createTimeDisplay) { this.createTimeDisplay = createTimeDisplay; }

        @Override
        public String toString() {
            return "ProductVO{" +
                    "name='" + name + '\'' +
                    ", priceDisplay='" + priceDisplay + '\'' +
                    ", createTimeDisplay='" + createTimeDisplay + '\'' +
                    '}';
        }
    }

    public static class OrderDetailVO {
        // 从 UserEntity 获取
        @MapField(sourceIndex = 0, sourcePath = "id")
        private Long userId;

        @MapField(sourceIndex = 0, sourcePath = "name")
        private String userName;

        // 从 OrderEntity 获取
        @MapField(sourceIndex = 1, sourcePath = "orderNo")
        private String orderNumber;

        @MapField(sourceIndex = 1, sourcePath = "totalAmount", format = "¥#,##0.00")
        private String amountDisplay;

        @MapField(sourceIndex = 1, sourcePath = "createTime", format = "yyyy-MM-dd HH:mm:ss")
        private String createTimeDisplay;

        // Getter and Setter
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public String getAmountDisplay() { return amountDisplay; }
        public void setAmountDisplay(String amountDisplay) { this.amountDisplay = amountDisplay; }
        public String getCreateTimeDisplay() { return createTimeDisplay; }
        public void setCreateTimeDisplay(String createTimeDisplay) { this.createTimeDisplay = createTimeDisplay; }

        @Override
        public String toString() {
            return "OrderDetailVO{" +
                    "userId=" + userId +
                    ", userName='" + userName + '\'' +
                    ", orderNumber='" + orderNumber + '\'' +
                    ", amountDisplay='" + amountDisplay + '\'' +
                    ", createTimeDisplay='" + createTimeDisplay + '\'' +
                    '}';
        }
    }

    // ========== 自定义处理器 ==========

    /**
     * 自定义字段处理器
     * 演示如何扩展处理器
     */
    public static class CustomFieldHandler implements FieldMappingHandler {

        @Override
        public Object handle(MappingContext context, Field targetField, Object sourceValue) {
            // 示例：将年龄字段加1
            if (targetField.getName().equals("age") && sourceValue instanceof Integer) {
                return (Integer) sourceValue + 1;
            }
            return sourceValue;
        }

        @Override
        public boolean supports(MappingContext context, Field targetField) {
            // 只处理 age 字段
            return targetField.getName().equals("age");
        }

        @Override
        public int getPriority() {
            return 50;
        }

        @Override
        public String getName() {
            return "CustomFieldHandler";
        }
    }
}
