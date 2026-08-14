# DSL 参考

DSL 版本为 `1.0`。关键字与函数编码按 ASCII 大小写不敏感处理；字符串内容和事实路径大小写保持不变。

## 字面量

| 字面量 | 语法 | 示例 | 契约 `TypeKind` | 运行期 Java 值 |
|--------|------|------|-----------------|-----------------|
| 字符串 | 单引号或双引号 | `'paid'`、`"VIP"` | `STRING` | `String` |
| 布尔 | `true` / `false` | `TRUE` | `BOOLEAN` | `Boolean` |
| 整数 | 十进制数字，一元负号独立解析 | `0`、`-42` | `INTEGER` | `BigInteger` |
| 小数 | 数字`.`数字，不支持指数写法 | `12.50`、`-0.25` | `DECIMAL` | `BigDecimal` |
| 空值 | `null` | `${name} = null` | `NULL` | `null` |
| 日期 | `DATE` 后接字符串 | `DATE '2026-08-14'` | `DATE` | `LocalDate` |
| 本地日期时间 | `DATETIME` 后接字符串 | `DATETIME '2026-08-14T10:30:45'` | `DATE_TIME` | `LocalDateTime` |
| 时间点 | `INSTANT` 后接字符串 | `INSTANT '2026-08-14T02:30:45Z'` | `INSTANT` | `Instant` |

时间格式分别由 JDK `LocalDate.parse`、`LocalDateTime.parse` 和 `Instant.parse` 校验。`DATE` 必须是 ISO 本地日期；`DATETIME` 不带时区或偏移；`INSTANT` 必须是可解析的 UTC 时间点文本，例如带 `Z`。`DATE '2026/08/14'`、`DATETIME '2026-08-14 10:30:45'`、`INSTANT '2026-08-14T10:30:45'` 都会编译失败。

字符串只支持 `\\`、`\'`、`\"`、`\n`、`\r`、`\t` 六类转义。不支持 `\uXXXX` 或任意未知转义。

## 事实路径与函数

事实引用必须写成 `${path}`：

```text
${customer.age}
${order.items[0].price}
${_internal.code2}
```

属性名首字符只能是 ASCII 字母或下划线，后续可加 ASCII 数字；下标必须是非负十进制整数。路径不支持通配符、方法调用、键表达式或 `.class`。

函数调用写成 `$CODE(arguments)`，编码以 ASCII 字母开头，后续可含数字和下划线，最长 128 个字符；编译时规范化为大写：

```text
$ROUND(${order.amount}, 2)
$NOW()
```

函数必须先注册，参数数量和类型在编译期检查。函数返回类型也参与整个表达式的类型推导。

## 运算符与类型

| 类别 | 运算符 | 规则 |
|------|--------|------|
| 算术 | `+ - * / %` | 仅 `INTEGER`、`DECIMAL`；整数与小数混合提升为小数；不做字符串拼接 |
| 相等 | `= !=` | 同类标量或数值混合；`NULL` 可与任意类型判断相等 |
| 排序 | `> >= < <=` | 数值混合，或同类型 `STRING`、`DATE`、`DATETIME`、`INSTANT` |
| 成员 | `IN`、`NOT IN` | 左值与右侧每个元素必须满足相等兼容规则 |
| 区间 | `BETWEEN ... AND ...` | 闭区间，值、下界和上界必须属于同一可排序域 |
| 空值 | `IS NULL`、`IS NOT NULL` | 接受任意标量 |
| 逻辑 | `NOT`、`AND`、`OR` | 仅布尔值；`AND`、`OR` 短路 |

整数运算精确使用 `BigInteger`；整数 `/` 使用 `BigInteger.divide`，因此 `5 / 2` 为整数 `2`。只要一侧为 `DECIMAL`，结果为 `DECIMAL`；小数除法和取余使用 `MathContext.DECIMAL128`。除零和其他算术失败在求值期返回 `EVALUATION_ERROR`。

`IN` 的右侧是专用、非空的括号表达式列表：

```text
${status} IN ('PAID', 'SHIPPED')
${score} NOT IN (0, 1 + 1)
```

该列表只服务于 `IN`/`NOT IN`，不是可单独赋值、返回或传入函数的通用 collection literal。当前也没有集合遍历、量词或聚合语法。

## 优先级和结合性

从高到低：

| 优先级 | 构造 | 结合性 |
|--------|------|--------|
| 1 | 字面量、`${path}`、函数调用、括号 | — |
| 2 | 一元 `NOT`、一元 `+`、一元 `-` | 右结合 |
| 3 | `* / %` | 左结合 |
| 4 | `+ -` | 左结合 |
| 5 | 比较、`IN`、`NOT IN`、`BETWEEN`、`IS NULL` | 不可链式连接 |
| 6 | `AND` | 左结合 |
| 7 | `OR` | 左结合 |

例如 `1 + 2 * 3` 等于 `7`；`NOT ${active} OR ${admin} AND true` 先计算一元 `NOT`，再计算 `AND`，最后计算 `OR`。`1 < 2 < 3` 会被拒绝，必须拆成 `1 < 2 AND 2 < 3`。

## NULL 语义

`null = null` 为真，`null != 1` 为真；`${nullable} IS NULL` 是推荐的显式空判断。`NULL` 不会隐式转换为零、空字符串或 `false`，也不会自动传播。字面量 `null` 不能参与排序、算术、逻辑、`IN` 或 `BETWEEN`。若契约允许为空的数值或布尔路径在运行时实际为 `null`，相关算术、排序或逻辑求值会失败；宿主应先用短路空判断保护：

```text
${amount} IS NOT NULL AND ${amount} > 100
```

## 明确禁止与错误示例

```text
amount > 100                 # 裸标识符；应写 ${amount}
${items[*].price} > 10       # 通配路径未实现
${text} + 'suffix'           # 不支持字符串拼接
'1' = 1                     # 不做字符串到数字的隐式转换
${date} < ${dateTime}        # 不跨 DATE / DATETIME 类型转换
${x} IN ()                   # IN 列表不能为空
[1, 2, 3]                    # 没有通用集合字面量
DATE 20260814                # 时间前缀后必须是字符串字面量
$MISSING(${x})               # 未注册函数
```

所有规则语法和类型错误都通过 `CompilationResult` 的结构化诊断返回；调用方不应解析异常文本来识别错误。
