# letool-starter-data

## 模块简介

数据库封装模块，基于 Spring JdbcTemplate 增强，提供类型安全的 Lambda 查询、注解驱动的对象映射、自动分页、自增主键回填和多数据源方言适配能力。无需 MyBatis/Hibernate 等重型 ORM，适合追求轻量、高性能 SQL 控制的场景。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-data</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

**Step 1** — 定义实体类，用注解声明映射关系：

```java
@Table("sys_user")
public class User {
    @Id
    private Long id;

    @Column("user_name")
    private String userName;

    private Integer status;

    @Transient
    private List<Role> roles;   // 非数据库字段，自动忽略
}
```

**Step 2** — 注入 LetoolTemplate，执行 CRUD：

```java
@Autowired
private LetoolTemplate letoolTemplate;

// 插入
User user = new User();
user.setUserName("张三");
user.setStatus(1);
letoolTemplate.insert(user);
System.out.println(user.getId());  // 自增ID已自动回填

// Lambda 条件查询
List<User> users = letoolTemplate.lambdaQuery(User.class)
        .eq(User::getStatus, 1)
        .like(User::getUserName, "张")
        .orderByDesc(User::getId)
        .list()
        .stream().collect(Collectors.toList());

// 分页查询
PaginationResult<User> page = letoolTemplate.selectPage(
        letoolTemplate.lambdaQuery(User.class).eq(User::getStatus, 1),
        1, 20);

// 更新
user.setUserName("李四");
letoolTemplate.update(user);

// 按 ID 删除
letoolTemplate.deleteById(User.class, 1L);
```

## 配置属性

```yaml
letool:
  data:
    enabled: true               # 是否启用，默认 true
    pagination:
      max-page-size: 1000       # 单页最大条数，防止恶意查询，默认 1000
      default-page-size: 20     # 默认每页条数，默认 20
    mapping:
      auto-camel-case: true     # 下划线列名自动转驼峰，默认 true
      use-generated-keys: true  # INSERT 自动回填自增主键，默认 true
```

## 核心 API 示例

### 1. 注解声明式映射

四个核心注解组合使用，真正零配置映射：

```java
@Table("sys_order")           // 类 → 表名，未标注则自动驼峰转下划线
public class Order {
    @Id                        // 标记主键（支持 Long/Integer）
    private Long id;

    @Column("order_status")    // 字段 → 列名，未标注则自动推导
    private Integer orderStatus;

    @Transient                 // 跳过数据库映射（临时字段、关联数据等）
    private BigDecimal totalAmount;
}
```

### 2. 编程式 Lambda 查询（类型安全）

通过方法引用构建 WHERE 条件，编译期类型检查，避免列名拼写错误：

```java
// 等值、模糊、区间、集合、NULL 全覆盖
List<Order> list = letoolTemplate.lambdaQuery(Order.class)
        .select("id", "order_status")              // 指定查询列
        .eq(Order::getStatus, 1)                   // =
        .like(Order::getCustomerName, "张三")       // LIKE (%自动包裹)
        .between(Order::getCreateTime, start, end)  // BETWEEN
        .in(Order::getType, Arrays.asList(1, 2, 3)) // IN
        .isNotNull(Order::getPayTime)               // IS NOT NULL
        .orderByDesc(Order::getCreateTime)          // ORDER BY DESC
        .list();
```

Lambda 解析采用序列化机制 —— `SFunction<T, R>` 继承 `Function` 和 `Serializable`，Lambda 序列化`writeReplace` 暴露方法名后自动转 snake_case 映射到数据库列。

### 3. 编程式分页查询

```java
PaginationResult<User> page = letoolTemplate.selectPage(
        letoolTemplate.lambdaQuery(User.class)
                .ge(User::getAge, 18)
                .orderByAsc(User::getId),
        1, 20);

System.out.println("总记录数: " + page.getTotal());
System.out.println("总页数: " + page.getTotalPages());
System.out.println("当页数据: " + page.getRecords());
```

### 4. 原始 SQL 兜底

```java
// 自定义 RowMapper
List<UserDto> dtos = letoolTemplate.query(
        "SELECT u.*, o.amount FROM user u LEFT JOIN order o ON u.id = o.user_id",
        new BeanPropertyRowMapper<>(UserDto.class, true), param1, param2);

// 写操作
letoolTemplate.execute("DELETE FROM sys_log WHERE create_time < ?", cutoffDate);
```

### 5. 多数据源方言

构造时通过 JDBC URL 自动检测数据库类型，支持 MySQL 和 PostgreSQL 分页方言。URL 包含 `mysql` 用 `MySqlDialect`（LIMIT/OFFSET），包含 `postgresql` 用 `PostgreSqlDialect`，检测失败则回退为 MySQL 方言。
