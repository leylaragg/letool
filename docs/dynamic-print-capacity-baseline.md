# 动态打印容量基线

本文记录 `letool-starter-print-spring-boot` 三类固定 PDF 场景在明确环境中的首次基线。它用于观察版本变化，不是跨机器通用的性能承诺，也不作为固定毫秒门禁。

## 运行方式

普通 `mvn test` 会排除容量测试。需要在项目根目录显式执行：

```powershell
mvn --% -P print-capacity -pl letool-starter-print-spring-boot -am -Dtest=PrintCapacityBaselineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

测试先预热每个场景一次，再完整执行十次。每次都通过 `PrintService` 完成模板读取、上下文适配、编译缓存、绑定和 PDF 渲染，并使用 PDFBox 重新打开产物核对页数。

即时报告写入 `letool-starter-print-spring-boot/target/print-capacity/capacity-baseline.md`，不会进入版本控制。

## 固定场景

| 场景 | 固定输入 |
|---|---|
| `small-text` | 标题、两个普通段落和字段绑定 |
| `medium-table` | 一行表头、120 行动态表体和跨页重复表头 |
| `large-toc-annotations` | 16 章、全局目录、显式分页和 4 个文本便签批注 |

三个场景都使用 Letool XML、上下文版本 `1`、默认 A4 页面、默认渲染限制和同一套无外部字体配置。测试只断言产物合法、页数与字节数未突破框架限制，不断言固定耗时。

## 2026-08-18 首次结果

运行环境：

- Java：17.0.11，Java HotSpot 64-Bit Server VM
- 操作系统：Windows 11 10.0 amd64
- 可用处理器：28
- JVM 最大堆：8,547,991,552 bytes

| 场景 | 成功次数 | 页数 | 产物字节数 | 中位耗时 | P95 耗时 |
|---|---:|---:|---:|---:|---:|
| `small-text` | 10 | 1 | 1,091 | 87.472 ms | 167.167 ms |
| `medium-table` | 10 | 11 | 7,714 | 1,494.142 ms | 1,571.450 ms |
| `large-toc-annotations` | 10 | 17 | 8,462 | 877.956 ms | 1,048.407 ms |

## 如何比较后续结果

比较前应保持 JDK、字体、机器资源、模板字节、上下文数量和渲染限制一致。若场景页数或产物大小变化，先确认是否属于排版或功能变更，再判断耗时差异；不要只凭单次最快值认定性能改善。

CI 可以保留即时报告作为构建附件，但不应在共享、负载不稳定的执行器上设置绝对毫秒阈值。需要建立正式门禁时，应先使用固定硬件收集多次基线，再单独确定可接受的回归区间。
