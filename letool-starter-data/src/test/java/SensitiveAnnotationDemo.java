
import com.github.leyland.data.desensitize.Sensitive;
import com.github.leyland.data.desensitize.SensitiveType;
import com.github.leyland.data.mapper.ObjectMapper;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Sensitive 注解测试案例
 * 演示如何在 VO 中使用 @Sensitive 注解进行自动脱敏
 *
 * @author leyland
 * @date 2025-01-08
 */
public class SensitiveAnnotationDemo {

    public static void main(String[] args) {
        // ========== 测试1: 预定义类型脱敏 ==========
        testPredefinedTypes();

        // ========== 测试2: 自定义滑块脱敏 ==========
        testCustomSlide();

        // ========== 测试3: 自定义正则脱敏 ==========
        testCustomRegex();

        // ========== 测试4: 自定义索引脱敏 ==========
        testCustomIndex();

        // ========== 测试5: 混合使用多种脱敏方式 ==========
        testMixedDesensitize();

        // ========== 测试6: 与 ObjectMapper 集成测试 ==========
        testWithObjectMapper();
    }

    /**
     * 测试1: 预定义类型脱敏
     */
    private static void testPredefinedTypes() {
        System.out.println("========== 测试1: 预定义类型脱敏 ==========");

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setName("张三");
        user.setIdCard("430123199001011234");
        user.setPhone("13800138000");
        user.setEmail("zhangsan@example.com");
        user.setBankCard("6222021234567890123");
        user.setAddress("北京市朝阳区xxx街道xxx号");
        user.setPassword("MyPassword123");

        UserVO vo = new UserVO();
        ObjectMapper.map(vo, user);

        System.out.println("原始数据:");
        System.out.println("  姓名: " + user.getName());
        System.out.println("  身份证: " + user.getIdCard());
        System.out.println("  手机号: " + user.getPhone());
        System.out.println("  邮箱: " + user.getEmail());
        System.out.println("  银行卡: " + user.getBankCard());
        System.out.println("  地址: " + user.getAddress());
        System.out.println("  密码: " + user.getPassword());

        System.out.println("\n脱敏后数据:");
        System.out.println("  姓名: " + vo.getName());
        System.out.println("  身份证: " + vo.getIdCard());
        System.out.println("  手机号: " + vo.getPhone());
        System.out.println("  邮箱: " + vo.getEmail());
        System.out.println("  银行卡: " + vo.getBankCard());
        System.out.println("  地址: " + vo.getAddress());
        System.out.println("  密码: " + vo.getPassword());
        System.out.println();
    }

    /**
     * 测试2: 自定义滑块脱敏
     */
    private static void testCustomSlide() {
        System.out.println("========== 测试2: 自定义滑块脱敏 ==========");

        CustomSlideEntity entity = new CustomSlideEntity();
        entity.setCustomField1("12345678901234567890");
        entity.setCustomField2("ABCDEFGHIJKLMNOPQRST");
        entity.setCustomField3("9876543210");

        CustomSlideVO vo = new CustomSlideVO();
        ObjectMapper.map(vo, entity);

        System.out.println("原始数据:");
        System.out.println("  字段1: " + entity.getCustomField1());
        System.out.println("  字段2: " + entity.getCustomField2());
        System.out.println("  字段3: " + entity.getCustomField3());

        System.out.println("\n脱敏后数据:");
        System.out.println("  字段1: " + vo.getCustomField1() + " (保留前6后6)");
        System.out.println("  字段2: " + vo.getCustomField2() + " (保留前4后4, 掩码###)");
        System.out.println("  字段3: " + vo.getCustomField3() + " (保留前2后2, 反转)");
        System.out.println();
    }

    /**
     * 测试3: 自定义正则脱敏
     */
    private static void testCustomRegex() {
        System.out.println("========== 测试3: 自定义正则脱敏 ==========");

        CustomRegexEntity entity = new CustomRegexEntity();
        entity.setPhone("13800138000");
        entity.setIdCard("430123199001011234");
        entity.setSerial("SN1234567890XYZ");

        CustomRegexVO vo = new CustomRegexVO();
        ObjectMapper.map(vo, entity);

        System.out.println("原始数据:");
        System.out.println("  手机号: " + entity.getPhone());
        System.out.println("  身份证: " + entity.getIdCard());
        System.out.println("  序列号: " + entity.getSerial());

        System.out.println("\n脱敏后数据:");
        System.out.println("  手机号: " + vo.getPhone() + " (正则: (\\d{3})\\d{4}(\\d{4}) -> $1****$2)");
        System.out.println("  身份证: " + vo.getIdCard() + " (正则: (\\d{6})\\d{8}(\\d{4}) -> $1********$2)");
        System.out.println("  序列号: " + vo.getSerial() + " (正则: (SN\\d{4})\\d{4}(.*) -> $1****$2)");
        System.out.println();
    }

    /**
     * 测试4: 自定义索引脱敏
     */
    private static void testCustomIndex() {
        System.out.println("========== 测试4: 自定义索引脱敏 ==========");

        CustomIndexEntity entity = new CustomIndexEntity();
        entity.setField1("12345678901234567890");
        entity.setField2("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        entity.setField3("9876543210");

        CustomIndexVO vo = new CustomIndexVO();
        ObjectMapper.map(vo, entity);

        System.out.println("原始数据:");
        System.out.println("  字段1: " + entity.getField1());
        System.out.println("  字段2: " + entity.getField2());
        System.out.println("  字段3: " + entity.getField3());

        System.out.println("\n脱敏后数据:");
        System.out.println("  字段1: " + vo.getField1() + " (规则: 0,2-4,6,8-)");
        System.out.println("  字段2: " + vo.getField2() + " (规则: 5-10,15-20, 掩码#)");
        System.out.println("  字段3: " + vo.getField3() + " (规则: 1,3-5,7, 掩码0)");
        System.out.println();
    }

    /**
     * 测试5: 混合使用多种脱敏方式
     */
    private static void testMixedDesensitize() {
        System.out.println("========== 测试5: 混合使用多种脱敏方式 ==========");

        MixedDesensitizeEntity entity = new MixedDesensitizeEntity();
        entity.setName("李四");
        entity.setPhone("13900139000");
        entity.setIdCard("320111198505056789");
        entity.setOrderNo("ORD-20250108-001");
        entity.setTrackingNumber("SF1234567890123");

        MixedDesensitizeVO vo = new MixedDesensitizeVO();
        ObjectMapper.map(vo, entity);

        System.out.println("原始数据:");
        System.out.println("  姓名: " + entity.getName());
        System.out.println("  手机号: " + entity.getPhone());
        System.out.println("  身份证: " + entity.getIdCard());
        System.out.println("  订单号: " + entity.getOrderNo());
        System.out.println("  快递单号: " + entity.getTrackingNumber());

        System.out.println("\n脱敏后数据:");
        System.out.println("  姓名: " + vo.getName() + " (预定义: CHINESE_NAME)");
        System.out.println("  手机号: " + vo.getPhone() + " (预定义: MOBILE_PHONE)");
        System.out.println("  身份证: " + vo.getIdCard() + " (预定义: ID_CARD)");
        System.out.println("  订单号: " + vo.getOrderNo() + " (自定义滑块: 保留前7后3)");
        System.out.println("  快递单号: " + vo.getTrackingNumber() + " (自定义索引: 2-6,8-10)");
        System.out.println();
    }

    /**
     * 测试6: 与 ObjectMapper 集成测试
     */
    private static void testWithObjectMapper() {
        System.out.println("========== 测试6: 与 ObjectMapper 集成测试 ==========");

        // 模拟多个数据源
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setName("王五");
        user.setPhone("13700137000");
        user.setEmail("wangwu@example.com");

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo("ORDER-2025-001");
        orderInfo.setPaymentAccount("6228481234567890");
        orderInfo.setAmount(new BigDecimal("5999.00"));

        // 批量映射（实际场景可能返回 List）
        java.util.List<UserEntity> userList = java.util.Arrays.asList(user);
        java.util.List<UserDetailVO> voList = ObjectMapper.mapList(userList, UserDetailVO.class, orderInfo);

        System.out.println("批量映射结果:");
        for (UserDetailVO vo : voList) {
            System.out.println("  用户ID: " + vo.getUserId());
            System.out.println("  姓名: " + vo.getUserName());
            System.out.println("  手机: " + vo.getUserPhone());
            System.out.println("  邮箱: " + vo.getUserEmail());
            System.out.println("  订单号: " + vo.getOrderNo());
            System.out.println("  支付账号: " + vo.getPaymentAccount() + " (自动脱敏)");
            System.out.println("  金额: " + vo.getAmount());
        }
        System.out.println();
    }

    // ========== 测试实体类 ==========

    /**
     * 用户实体
     */
    public static class UserEntity {
        private Long id;
        private String name;
        private String idCard;
        private String phone;
        private String email;
        private String bankCard;
        private String address;
        private String password;

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
        public String getBankCard() { return bankCard; }
        public void setBankCard(String bankCard) { this.bankCard = bankCard; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /**
     * 订单信息
     */
    public static class OrderInfo {
        private String orderNo;
        private String paymentAccount;
        private BigDecimal amount;

        // Getter and Setter
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getPaymentAccount() { return paymentAccount; }
        public void setPaymentAccount(String paymentAccount) { this.paymentAccount = paymentAccount; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    // ========== 测试 VO 类 ==========

    /**
     * 用户VO（使用预定义脱敏类型）
     */
    public static class UserVO {
        @Sensitive(SensitiveType.CHINESE_NAME)
        private String name;

        @Sensitive(SensitiveType.ID_CARD)
        private String idCard;

        @Sensitive(SensitiveType.MOBILE_PHONE)
        private String phone;

        @Sensitive(SensitiveType.EMAIL)
        private String email;

        @Sensitive(SensitiveType.BANK_CARD)
        private String bankCard;

        @Sensitive(SensitiveType.ADDRESS)
        private String address;

        @Sensitive(SensitiveType.PASSWORD)
        private String password;

        // Getter and Setter
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
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
     * 自定义滑块脱敏 VO
     */
    public static class CustomSlideVO {
        @Sensitive(
            value = SensitiveType.CUSTOM_SLIDE,
            leftKeep = 6,
            rightKeep = 6,
            maskString = "*",
            reverse = false
        )
        private String customField1;

        @Sensitive(
            value = SensitiveType.CUSTOM_SLIDE,
            leftKeep = 4,
            rightKeep = 4,
            maskString = "###",
            reverse = false
        )
        private String customField2;

        @Sensitive(
            value = SensitiveType.CUSTOM_SLIDE,
            leftKeep = 2,
            rightKeep = 2,
            maskString = "*",
            reverse = true
        )
        private String customField3;

        // Getter and Setter
        public String getCustomField1() { return customField1; }
        public void setCustomField1(String customField1) { this.customField1 = customField1; }
        public String getCustomField2() { return customField2; }
        public void setCustomField2(String customField2) { this.customField2 = customField2; }
        public String getCustomField3() { return customField3; }
        public void setCustomField3(String customField3) { this.customField3 = customField3; }
    }

    /**
     * 自定义滑块脱敏实体
     */
    public static class CustomSlideEntity {
        private String customField1;
        private String customField2;
        private String customField3;

        // Getter and Setter
        public String getCustomField1() { return customField1; }
        public void setCustomField1(String customField1) { this.customField1 = customField1; }
        public String getCustomField2() { return customField2; }
        public void setCustomField2(String customField2) { this.customField2 = customField2; }
        public String getCustomField3() { return customField3; }
        public void setCustomField3(String customField3) { this.customField3 = customField3; }
    }

    /**
     * 自定义正则脱敏 VO
     */
    public static class CustomRegexVO {
        @Sensitive(
            value = SensitiveType.CUSTOM_REGEX,
            regex = "(\\d{3})\\d{4}(\\d{4})",
            replacement = "$1****$2"
        )
        private String phone;

        @Sensitive(
            value = SensitiveType.CUSTOM_REGEX,
            regex = "(\\d{6})\\d{8}(\\d{4})",
            replacement = "$1********$2"
        )
        private String idCard;

        @Sensitive(
            value = SensitiveType.CUSTOM_REGEX,
            regex = "(SN\\d{4})\\d{4}(.*)",
            replacement = "$1****$2"
        )
        private String serial;

        // Getter and Setter
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getSerial() { return serial; }
        public void setSerial(String serial) { this.serial = serial; }
    }

    /**
     * 自定义正则脱敏实体
     */
    public static class CustomRegexEntity {
        private String phone;
        private String idCard;
        private String serial;

        // Getter and Setter
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getSerial() { return serial; }
        public void setSerial(String serial) { this.serial = serial; }
    }

    /**
     * 自定义索引脱敏 VO
     */
    public static class CustomIndexVO {
        @Sensitive(
            value = SensitiveType.CUSTOM_INDEX,
            indexRules = {"0", "2-4", "6", "8-"},
            maskString = "*",
            reverse = false
        )
        private String field1;

        @Sensitive(
            value = SensitiveType.CUSTOM_INDEX,
            indexRules = {"5-10", "15-20"},
            maskString = "#",
            reverse = false
        )
        private String field2;

        @Sensitive(
            value = SensitiveType.CUSTOM_INDEX,
            indexRules = {"1", "3-5", "7"},
            maskString = "0",
            reverse = false
        )
        private String field3;

        // Getter and Setter
        public String getField1() { return field1; }
        public void setField1(String field1) { this.field1 = field1; }
        public String getField2() { return field2; }
        public void setField2(String field2) { this.field2 = field2; }
        public String getField3() { return field3; }
        public void setField3(String field3) { this.field3 = field3; }
    }

    /**
     * 自定义索引脱敏实体
     */
    public static class CustomIndexEntity {
        private String field1;
        private String field2;
        private String field3;

        // Getter and Setter
        public String getField1() { return field1; }
        public void setField1(String field1) { this.field1 = field1; }
        public String getField2() { return field2; }
        public void setField2(String field2) { this.field2 = field2; }
        public String getField3() { return field3; }
        public void setField3(String field3) { this.field3 = field3; }
    }

    /**
     * 混合脱敏 VO
     */
    public static class MixedDesensitizeVO {
        @Sensitive(SensitiveType.CHINESE_NAME)
        private String name;

        @Sensitive(SensitiveType.MOBILE_PHONE)
        private String phone;

        @Sensitive(SensitiveType.ID_CARD)
        private String idCard;

        @Sensitive(
            value = SensitiveType.CUSTOM_SLIDE,
            leftKeep = 7,
            rightKeep = 3
        )
        private String orderNo;

        @Sensitive(
            value = SensitiveType.CUSTOM_INDEX,
            indexRules = {"2-6", "8-10"},
            maskString = "*"
        )
        private String trackingNumber;

        // Getter and Setter
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    }

    /**
     * 混合脱敏实体
     */
    public static class MixedDesensitizeEntity {
        private String name;
        private String phone;
        private String idCard;
        private String orderNo;
        private String trackingNumber;

        // Getter and Setter
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getIdCard() { return idCard; }
        public void setIdCard(String idCard) { this.idCard = idCard; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    }

    /**
     * 用户详情VO
     */
    public static class UserDetailVO {

        @Sensitive(SensitiveType.CHINESE_NAME)
        @com.github.leyland.data.mapper.MapFrom(sourceIndex = 0, value = "name")
        private String userName;

        @Sensitive(SensitiveType.MOBILE_PHONE)
        @com.github.leyland.data.mapper.MapFrom(sourceIndex = 0, value = "phone")
        private String userPhone;

        @Sensitive(SensitiveType.EMAIL)
        @com.github.leyland.data.mapper.MapFrom(sourceIndex = 0, value = "email")
        private String userEmail;

        @com.github.leyland.data.mapper.MapFrom(sourceIndex = 0, value = "id")
        private Long userId;

        @com.github.leyland.data.mapper.MapFrom(sourceIndex = 1, value = "orderNo")
        private String orderNo;

        @Sensitive(SensitiveType.BANK_CARD)
        @com.github.leyland.data.mapper.MapFrom(sourceIndex = 1, value = "paymentAccount")
        private String paymentAccount;

        @com.github.leyland.data.mapper.MapFrom(sourceIndex = 1, value = "amount")
        private BigDecimal amount;

        // Getter and Setter
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getUserPhone() { return userPhone; }
        public void setUserPhone(String userPhone) { this.userPhone = userPhone; }
        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getPaymentAccount() { return paymentAccount; }
        public void setPaymentAccount(String paymentAccount) { this.paymentAccount = paymentAccount; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}
