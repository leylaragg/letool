import com.github.leyland.data.mapper.MapFrom;
import com.github.leyland.data.mapper.ObjectMapper;

import java.util.Date;

/**
 * 对象映射器演示
 * 展示如何使用 ObjectMapper 进行对象映射
 *
 * @author leyland
 * @date 2025-01-08
 */
public class ObjectMapperDemo {

    public static void main(String[] args) {
        // ========== 基础示例 ==========
        basicExample();

        // ========== 多源对象示例 ==========
        multiSourceExample();

        // ========== 嵌套对象示例 ==========
        nestedObjectExample();

        // ========== 类型转换示例 ==========
        typeConversionExample();

        // ========== 自定义转换器示例 ==========
        customConverterExample();
    }

    /**
     * 基础示例：单个源对象映射到VO
     */
    private static void basicExample() {
        System.out.println("========== 基础示例 ==========");

        // 创建源对象（实体类）
        UserEntity userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setName("张三");
        userEntity.setAge(25);
        userEntity.setEmail("zhangsan@example.com");

        // 创建目标VO对象
        UserVO userVO = new UserVO();

        // 执行映射
        ObjectMapper.map(userVO, userEntity);

        // 输出结果
        System.out.println("UserVO: " + userVO);
        System.out.println();
    }

    /**
     * 多源对象示例：从多个源对象映射到一个VO
     */
    private static void multiSourceExample() {
        System.out.println("========== 多源对象示例 ==========");

        // 第一个源对象：用户信息
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setName("李四");
        user.setAge(30);

        // 第二个源对象：订单信息
        OrderEntity order = new OrderEntity();
        order.setOrderNo("ORDER-2025-001");
        order.setTotalAmount(new java.math.BigDecimal("999.99"));

        // 第三个源对象：扩展信息
        UserExtraInfo extraInfo = new UserExtraInfo();
        extraInfo.setPhone("13800138000");
        extraInfo.setAddress("北京市朝阳区");

        // 创建目标VO
        UserDetailVO detailVO = new UserDetailVO();

        // 执行映射
        ObjectMapper.map(detailVO, user, order, extraInfo);

        // 输出结果
        System.out.println("UserDetailVO: " + detailVO);
        System.out.println();
    }

    /**
     * 嵌套对象示例：支持深层嵌套属性访问
     */
    private static void nestedObjectExample() {
        System.out.println("========== 嵌套对象示例 ==========");

        // 创建嵌套对象结构
        Address address = new Address();
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setStreet("科技园路");

        UserProfile profile = new UserProfile();
        profile.setAddress(address);
        profile.setNickname("技术达人");

        UserEntity user = new UserEntity();
        user.setId(2L);
        user.setName("王五");
        user.setProfile(profile);

        // 创建目标VO
        UserLocationVO locationVO = new UserLocationVO();

        // 执行映射（使用嵌套路径）
        ObjectMapper.map(locationVO, user);

        // 输出结果
        System.out.println("UserLocationVO: " + locationVO);
        System.out.println();
    }

    /**
     * 类型转换示例：自动进行类型转换
     */
    private static void typeConversionExample() {
        System.out.println("========== 类型转换示例 ==========");

        // 源对象：数值类型
        DataEntity dataEntity = new DataEntity();
        dataEntity.setIntValue(123);
        dataEntity.setDoubleValue(456.78);
        dataEntity.setBoolValue(true);
        dataEntity.setDateValue(new Date());

        // 目标VO：字符串类型
        DataVO dataVO = new DataVO();

        // 执行映射（自动类型转换）
        ObjectMapper.map(dataVO, dataEntity);

        // 输出结果
        System.out.println("DataVO: " + dataVO);
        System.out.println();
    }

    /**
     * 自定义转换器示例：使用自定义转换逻辑
     */
    private static void customConverterExample() {
        System.out.println("========== 自定义转换器示例 ==========");

        // 源对象
        ProductEntity product = new ProductEntity();
        product.setProductName("高性能笔记本");
        product.setPrice(new java.math.BigDecimal("5999.00"));
        product.setStock(100);
        product.setCreateTime(new Date());

        // 目标VO
        ProductVO productVO = new ProductVO();

        // 执行映射
        ObjectMapper.map(productVO, product);

        // 输出结果
        System.out.println("ProductVO: " + productVO);
        System.out.println();
    }

    // ========== 测试用的实体类和VO类 ==========

    /**
     * 用户实体
     */
    static class UserEntity {
        private Long id;
        private String name;
        private Integer age;
        private String email;
        private UserProfile profile;

        // Getter and Setter
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public UserProfile getProfile() { return profile; }
        public void setProfile(UserProfile profile) { this.profile = profile; }

        @Override
        public String toString() {
            return "UserEntity{id=" + id + ", name='" + name + "', age=" + age + ", email='" + email + "'}";
        }
    }

    /**
     * 用户资料（嵌套对象）
     */
    static class UserProfile {
        private String nickname;
        private Address address;

        // Getter and Setter
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    /**
     * 地址（深层嵌套对象）
     */
    static class Address {
        private String city;
        private String district;
        private String street;

        // Getter and Setter
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }

    /**
     * 订单实体
     */
    static class OrderEntity {
        private String orderNo;
        private java.math.BigDecimal totalAmount;

        // Getter and Setter
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public java.math.BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(java.math.BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }

    /**
     * 用户扩展信息
     */
    static class UserExtraInfo {
        private String phone;
        private String address;

        // Getter and Setter
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    /**
     * 产品实体
     */
    static class ProductEntity {
        private String productName;
        private java.math.BigDecimal price;
        private Integer stock;
        private Date createTime;

        // Getter and Setter
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }
    }

    /**
     * 数据实体
     */
    static class DataEntity {
        private Integer intValue;
        private Double doubleValue;
        private Boolean boolValue;
        private Date dateValue;

        // Getter and Setter
        public Integer getIntValue() { return intValue; }
        public void setIntValue(Integer intValue) { this.intValue = intValue; }
        public Double getDoubleValue() { return doubleValue; }
        public void setDoubleValue(Double doubleValue) { this.doubleValue = doubleValue; }
        public Boolean getBoolValue() { return boolValue; }
        public void setBoolValue(Boolean boolValue) { this.boolValue = boolValue; }
        public Date getDateValue() { return dateValue; }
        public void setDateValue(Date dateValue) { this.dateValue = dateValue; }
    }

    // ========== VO类 ==========

    /**
     * 用户VO（基础示例）
     */
    static class UserVO {
        @MapFrom
        private Long id;

        @MapFrom
        private String name;

        @MapFrom
        private Integer age;

        @MapFrom
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

    /**
     * 用户详情VO（多源对象）
     */
    static class UserDetailVO {
        // 从 UserEntity 获取
        @MapFrom(sourceIndex = 0, value = "id")
        private Long userId;

        @MapFrom(sourceIndex = 0, value = "name")
        private String userName;

        @MapFrom(sourceIndex = 0, value = "age")
        private Integer userAge;

        // 从 OrderEntity 获取
        @MapFrom(sourceIndex = 1, value = "orderNo")
        private String orderNumber;

        @MapFrom(sourceIndex = 1, value = "totalAmount")
        private java.math.BigDecimal amount;

        // 从 UserExtraInfo 获取
        @MapFrom(sourceIndex = 2, value = "phone")
        private String phoneNumber;

        @MapFrom(sourceIndex = 2, value = "address")
        private String fullAddress;

        // Getter and Setter
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public Integer getUserAge() { return userAge; }
        public void setUserAge(Integer userAge) { this.userAge = userAge; }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getFullAddress() { return fullAddress; }
        public void setFullAddress(String fullAddress) { this.fullAddress = fullAddress; }

        @Override
        public String toString() {
            return "UserDetailVO{" +
                    "userId=" + userId +
                    ", userName='" + userName + '\'' +
                    ", userAge=" + userAge +
                    ", orderNumber='" + orderNumber + '\'' +
                    ", amount=" + amount +
                    ", phoneNumber='" + phoneNumber + '\'' +
                    ", fullAddress='" + fullAddress + '\'' +
                    '}';
        }
    }

    /**
     * 用户位置VO（嵌套对象）
     */
    static class UserLocationVO {
        @MapFrom(value = "id")
        private Long id;

        @MapFrom(value = "name")
        private String userName;

        @MapFrom(value = "profile.nickname")
        private String nickname;

        @MapFrom(value = "profile.address.city")
        private String city;

        @MapFrom(value = "profile.address.district")
        private String district;

        @MapFrom(value = "profile.address.street")
        private String street;

        // Getter and Setter
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }

        @Override
        public String toString() {
            return "UserLocationVO{" +
                    "id=" + id +
                    ", userName='" + userName + '\'' +
                    ", nickname='" + nickname + '\'' +
                    ", city='" + city + '\'' +
                    ", district='" + district + '\'' +
                    ", street='" + street + '\'' +
                    '}';
        }
    }

    /**
     * 数据VO（类型转换）
     */
    static class DataVO {
        @MapFrom(value = "intValue")
        private String intStr;

        @MapFrom(value = "doubleValue")
        private String doubleStr;

        @MapFrom(value = "boolValue")
        private String boolStr;

        @MapFrom(value = "dateValue")
        private String dateStr;

        // Getter and Setter
        public String getIntStr() { return intStr; }
        public void setIntStr(String intStr) { this.intStr = intStr; }
        public String getDoubleStr() { return doubleStr; }
        public void setDoubleStr(String doubleStr) { this.doubleStr = doubleStr; }
        public String getBoolStr() { return boolStr; }
        public void setBoolStr(String boolStr) { this.boolStr = boolStr; }
        public String getDateStr() { return dateStr; }
        public void setDateStr(String dateStr) { this.dateStr = dateStr; }

        @Override
        public String toString() {
            return "DataVO{" +
                    "intStr='" + intStr + '\'' +
                    ", doubleStr='" + doubleStr + '\'' +
                    ", boolStr='" + boolStr + '\'' +
                    ", dateStr='" + dateStr + '\'' +
                    '}';
        }
    }

    /**
     * 产品VO（自定义转换器）
     */
    static class ProductVO {
        @MapFrom(value = "productName")
        private String name;

        @MapFrom(value = "price")
        private String priceDisplay;

        @MapFrom(value = "stock")
        private String stockStatus;

        @MapFrom(value = "createTime")
        private String createTimeDisplay;

        // Getter and Setter
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPriceDisplay() { return priceDisplay; }
        public void setPriceDisplay(String priceDisplay) { this.priceDisplay = priceDisplay; }
        public String getStockStatus() { return stockStatus; }
        public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
        public String getCreateTimeDisplay() { return createTimeDisplay; }
        public void setCreateTimeDisplay(String createTimeDisplay) { this.createTimeDisplay = createTimeDisplay; }

        @Override
        public String toString() {
            return "ProductVO{" +
                    "name='" + name + '\'' +
                    ", priceDisplay='" + priceDisplay + '\'' +
                    ", stockStatus='" + stockStatus + '\'' +
                    ", createTimeDisplay='" + createTimeDisplay + '\'' +
                    '}';
        }
    }
}
