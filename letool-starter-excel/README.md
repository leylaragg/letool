# letool-starter-excel

## 模块定位

`letool-starter-excel` 是 EasyExcel 之上的轻量工具层，不再重复实现列映射、
格式化和类型转换体系。模块只保留以下职责：

- 常用文件和流读写入口；
- 大文件分批读取；
- Letool 默认样式和列宽自适应；
- 基于 `@ExcelValidation` 的轻量行数据校验；
- 参数校验与统一 Excel 错误码。

本模块不创建 Spring Bean，也没有 `letool.excel.enabled` 配置项。引入依赖后可直接
调用静态 API；需要更复杂的多工作表、模板填充、合并单元格或自定义监听器时，
应直接使用 EasyExcel 原生 API。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-excel</artifactId>
    <version>${letool.version}</version>
</dependency>
```

EasyExcel 是本模块的运行时基础，会通过该依赖传递给应用。

## 快速开始

### 1. 定义行数据类型

字段映射、格式和转换器直接使用 EasyExcel 原生注解。读取对象应提供无参构造器
和对应 setter，写入对象应提供对应 getter。

```java
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.github.leylaragg.letool.excel.annotation.ExcelValidation;

public class UserDto {

    @ExcelProperty(value = "用户名", index = 0)
    @ColumnWidth(20)
    @ExcelValidation(required = true, message = "用户名不能为空")
    private String username;

    @ExcelProperty(value = "余额", index = 1)
    @NumberFormat("#,##0.00")
    private BigDecimal balance;

    @ExcelProperty(value = "出生日期", index = 2)
    @DateTimeFormat("yyyy-MM-dd")
    private LocalDate birthday;

    public UserDto() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    // 其余字段同样提供 getter 和 setter。
}
```

### 2. 读取与写入

```java
// 小数据量：全部加载到内存。
List<UserDto> users = ExcelUtil.read("users.xlsx", UserDto.class);

// 指定工作表下标和表头行数。
List<UserDto> usersWithTwoHeaderRows =
        ExcelUtil.read("users.xlsx", UserDto.class, 0, 2);

// 写入文件，并应用 Letool 默认样式和列宽自适应。
ExcelUtil.write("export.xlsx", "用户列表", users, UserDto.class);

// 写入调用方提供的流。
ExcelUtil.write(outputStream, "用户列表", users, UserDto.class);
```

传入 `InputStream` 或 `OutputStream` 时，`ExcelUtil` 不会关闭流，调用方必须负责
流的生命周期。

### 3. 分批读取

```java
ExcelUtil.batchRead("large.xlsx", UserDto.class, 500, batch -> {
    userService.batchSave(batch);
});
```

回调在读取线程中同步执行，每批数据都是不可修改快照。`batchSize` 必须大于 0。
批次回调自身抛出的运行时异常会原样传播，不会被误包装成 Excel 读取异常。

### 4. 读取并校验

```java
ValidationResult result =
        ExcelUtil.readAndValidate("import.xlsx", UserDto.class);

if (result.hasErrors()) {
    for (ValidationResult.ValidationError error : result.getErrors()) {
        log.warn(
                "第{}行，字段[{}]：{}",
                error.getRow(),
                error.getField(),
                error.getMessage()
        );
    }
}

log.info("已校验 {} 行", result.getTotalRows());
```

`@ExcelValidation` 支持：

- `required`：非 `null` 且不能只包含空白字符；
- `minLength`：最小字符串长度；
- `maxLength`：最大字符串长度；
- `regex`：完整正则匹配；
- `message`：当前字段规则失败时使用的自定义消息。

`ValidationResult#getErrors()` 返回不可修改快照。无效正则、反射访问失败等规则执行
故障会抛出 `ExcelException`，不会被当成普通业务数据错误继续执行。

## 原生转换器扩展

自定义转换直接实现 EasyExcel 的 `Converter<T>`，并通过 `@ExcelProperty` 引用：

```java
public class StatusConverter implements Converter<Status> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return Status.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public Status convertToJavaData(
            ReadCellData<?> cellData,
            ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration) {
        return Status.valueOf(cellData.getStringValue().toUpperCase());
    }

    @Override
    public WriteCellData<?> convertToExcelData(
            Status value,
            ExcelContentProperty contentProperty,
            GlobalConfiguration globalConfiguration) {
        return new WriteCellData<>(value.name().toLowerCase());
    }
}
```

```java
@ExcelProperty(value = "状态", converter = StatusConverter.class)
private Status status;
```

转换器、监听器和写入处理器的完整扩展能力以 EasyExcel 原生 API 为准。

## 统一异常

| 错误码 | 异常场景 |
|---|---|
| `EXCEL_001` | 工作簿读取或解析失败 |
| `EXCEL_002` | 工作簿生成或写出失败 |
| `EXCEL_003` | 校验规则执行发生技术故障 |

以上异常均为 `ExcelException`，继承自 Letool 的 `SystemException`，并保留底层异常原因。
异常对外消息不会拼接文件内容、文件路径或底层实现消息。

参数为空、下标为负数、批次大小不合法等调用错误会直接抛出
`IllegalArgumentException`。

## 2.0 迁移说明

本次调整删除了未真正接入完整读写流程的 Letool 自定义映射体系，属于破坏性调整：

| 已删除用法 | 替代方式 |
|---|---|
| `@ExcelColumn(value = "...", index = n)` | `@ExcelProperty(value = "...", index = n)` |
| `@ExcelColumn(format = "yyyy-MM-dd")` | `@DateTimeFormat("yyyy-MM-dd")` |
| `@ExcelColumn(format = "0.00")` | `@NumberFormat("0.00")` |
| `@ExcelColumn(width = 20)` | `@ColumnWidth(20)` |
| `ExcelConverter<T>` | EasyExcel `Converter<T>` |
| `DateConverter` | `@DateTimeFormat` 或 EasyExcel 自定义 `Converter` |
| `EnumConverter` | EasyExcel 自定义 `Converter` |
| `letool.excel.enabled` | 删除；本模块没有自动配置和运行时 Bean |

旧实现会通过反射直接写入私有字段；迁移到 EasyExcel 原生映射后，读取实体应补齐
无参构造器和 setter。若业务必须保留特殊对象构造方式，应直接使用 EasyExcel
监听器完成显式映射。
