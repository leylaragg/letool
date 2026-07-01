# letool-starter-excel

## 模块简介

Excel 操作模块，基于 EasyExcel 封装，提供链式静态 API 简化 Excel 的读写操作。支持注解驱动列映射、分批处理避免 OOM、读取时数据校验，以及自动应用默认样式和列宽自适应。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-excel</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

**Step 1** — 定义数据实体，注解声明列映射：

```java
public class UserDto {
    @ExcelColumn("用户名")
    private String username;

    @ExcelColumn(value = "手机号", index = 1)
    private String phone;

    @ExcelColumn(value = "出生日期", format = "yyyy-MM-dd")
    private LocalDate birthday;

    @ExcelColumn(value = "状态", converter = "statusConverter")
    private String status;
}
```

**Step 2** — 一行代码读写 Excel：

```java
// 读取 —— 全部加载
List<UserDto> users = ExcelUtil.read("users.xlsx", UserDto.class);

// 写入 —— 自动样式 + 列宽自适应
ExcelUtil.write("export.xlsx", "用户列表", users, UserDto.class);

// 读取并校验
ValidationResult result = ExcelUtil.readAndValidate("import.xlsx", UserDto.class);
```

**Step 3** — Web 导出：

```java
@GetMapping("/export")
public void export(HttpServletResponse response) throws IOException {
    response.setContentType("application/vnd.ms-excel");
    response.setHeader("Content-Disposition", "attachment; filename=users.xlsx");
    ExcelUtil.write(response.getOutputStream(), "用户数据", userList, UserDto.class);
}
```

## 配置属性

```yaml
letool:
  excel:
    enabled: true               # 是否启用，默认 true
```

## 核心 API 示例

### 1. 注解声明式：@ExcelColumn

通过注解定义 Excel 列与 Java 字段的映射关系，支持列头名称、列索引、列宽、日期/数字格式化及自定义转换器：

```java
public class ProductDto {
    @ExcelColumn(value = "商品名称", index = 0, width = 20)
    private String name;

    @ExcelColumn(value = "价格", index = 1, format = "0.00")
    private BigDecimal price;

    @ExcelColumn(value = "上架时间", index = 2, format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ExcelColumn(value = "分类", converter = "categoryConverter")
    private String category;
}
```

`@ExcelColumn` 注解属性：
- `value()` — 列头名称
- `index()` — 列索引（-1 自动分配）
- `width()` — 列宽字符数（-1 自适应）
- `format()` — 格式化模式（日期、数字）
- `converter()` — 自定义转换器 Spring Bean 名称

### 2. 编程式：ExcelUtil 读写

所有方法均为静态方法，工具类不可实例化：

```java
// ===== 读取 =====
// 全部加载（小数据量）
List<UserDto> list = ExcelUtil.read("data.xlsx", UserDto.class);
List<UserDto> list2 = ExcelUtil.read("data.xlsx", UserDto.class, 0, 2); // sheet 0, 表头2行
List<UserDto> list3 = ExcelUtil.read(inputStream, UserDto.class);

// ===== 分批消费（大数据量，避免 OOM）=====
ExcelUtil.batchRead("large.xlsx", UserDto.class, 500, batch -> {
    userMapper.batchInsert(batch);   // 每 500 条写一次数据库
});

// ===== 写入 =====
ExcelUtil.write("export.xlsx", "数据表", dataList, DataDto.class);         // 写文件
ExcelUtil.write(outputStream, "数据表", dataList, DataDto.class);          // 写流（Web导出）
```

写入自动注册 `StyleTemplate.defaultStyle()`（边框、对齐等）和 `LongestMatchColumnWidthStyleStrategy`（列宽自适应）。

### 3. 编程式：读取时校验

边读边校验，行级隔离，所有校验错误收集到 `ValidationResult` 统一返回：

```java
ValidationResult result = ExcelUtil.readAndValidate("import.xlsx", UserDto.class);

if (result.hasErrors()) {
    for (ValidationResult.ValidationError err : result.getErrors()) {
        log.warn("第{}行 字段[{}]: {}", err.getRow(), err.getField(), err.getMessage());
    }
} else {
    log.info("数据校验通过，共{}行", result.getTotalRows());
}
```

校验规则通过 `@ExcelValidation` 注解在实体字段上声明（支持 required、regex 等规则），底层由 `DataValidator.validate()` 执行。

### 4. 分批读取完整示例

百万级数据导入，避免全量加载导致内存溢出：

```java
ExcelUtil.batchRead("huge_orders.xlsx", OrderDto.class, 1000, batch -> {
    jdbcTemplate.batchUpdate(
        "INSERT INTO t_order (order_no, amount) VALUES (?, ?)",
        batch, 1000,
        (ps, order) -> {
            ps.setString(1, order.getOrderNo());
            ps.setBigDecimal(2, order.getAmount());
        }
    );
});
```
