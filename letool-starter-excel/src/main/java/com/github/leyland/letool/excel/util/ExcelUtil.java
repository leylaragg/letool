package com.github.leyland.letool.excel.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.github.leyland.letool.excel.style.StyleTemplate;
import com.github.leyland.letool.excel.validation.DataValidator;
import com.github.leyland.letool.excel.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel操作工具类 —— 本模块的核心入口。
 *
 * <p>封装了 EasyExcel 的常用读写操作，提供一站式静态方法用于：
 * <ul>
 *   <li><b>读取Excel</b> —— 从文件路径或输入流读取，支持全部加载和分批消费两种模式</li>
 *   <li><b>写入Excel</b> —— 向文件路径或输出流写入，自动应用默认样式和列宽自适应</li>
 *   <li><b>读取并校验</b> —— 读取Excel的同时执行数据校验（配合 {@code @ExcelValidation} 注解）</li>
 * </ul>
 *
 * <p>该类为工具类（final + private构造器），不可实例化，所有方法均为静态方法。
 *
 * <h3>一、读取Excel</h3>
 *
 * <h4>1.1 全部加载（适用于小数据量）</h4>
 * <pre>{@code
 * // 从文件读取（默认sheet 0，表头1行）
 * List<UserDto> users = ExcelUtil.read("users.xlsx", UserDto.class);
 *
 * // 从文件读取（指定sheet和表头行数）
 * List<UserDto> users = ExcelUtil.read("users.xlsx", UserDto.class, 0, 2);
 *
 * // 从输入流读取
 * List<UserDto> users = ExcelUtil.read(inputStream, UserDto.class);
 * }</pre>
 *
 * <h4>1.2 分批消费（适用于大数据量）</h4>
 * <pre>{@code
 * // 每100条调用一次consumer，避免全量加载导致内存溢出
 * ExcelUtil.batchRead("large.xlsx", UserDto.class, 100, batch -> {
 *     userService.batchInsert(batch);
 * });
 * }</pre>
 *
 * <h3>二、写入Excel</h3>
 *
 * <pre>{@code
 * // 写入文件
 * List<UserDto> data = userService.listAll();
 * ExcelUtil.write("export.xlsx", "用户列表", data, UserDto.class);
 *
 * // 写入输出流（如HTTP响应）
 * ExcelUtil.write(response.getOutputStream(), "用户列表", data, UserDto.class);
 * }</pre>
 *
 * <h3>三、读取并校验</h3>
 *
 * <pre>{@code
 * ValidationResult result = ExcelUtil.readAndValidate("users.xlsx", UserDto.class);
 * if (result.hasErrors()) {
 *     for (ValidationResult.ValidationError err : result.getErrors()) {
 *         log.warn("第{}行 {}: {}", err.getRow(), err.getField(), err.getMessage());
 *     }
 * }
 * }</pre>
 *
 * <h3>四、配合注解使用</h3>
 *
 * <pre>{@code
 * // 实体类定义
 * public class UserDto {
 *     @ExcelColumn("用户名")
 *     @ExcelValidation(required = true, message = "用户名不能为空")
 *     private String username;
 *
 *     @ExcelColumn(value = "手机号", index = 1)
 *     @ExcelValidation(regex = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
 *     private String phone;
 *
 *     @ExcelColumn(value = "状态", converter = "statusConverter")
 *     private StatusEnum status;
 * }
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
public final class ExcelUtil {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(ExcelUtil.class);

    // ======================== 私有构造器 ========================

    /**
     * 私有构造器，防止实例化工具类。
     */
    private ExcelUtil() {}

    // ======================== 读取：文件路径 ========================

    /**
     * 从文件路径读取Excel（默认读取第一个sheet，表头占1行）。
     *
     * <p>便捷方法，等价于 {@code read(filePath, clazz, 0, 1)}。
     * 适用于小数据量的场景，会一次性将所有行加载到内存中。
     *
     * @param <T>      数据实体类型
     * @param filePath Excel文件路径，不能为 {@code null}
     * @param clazz    数据实体类，用于字段映射
     * @return 解析后的实体列表，不会为 {@code null}（无数据时返回空列表）
     */
    public static <T> List<T> read(String filePath, Class<T> clazz) {
        return read(filePath, clazz, 0, 1);
    }

    /**
     * 从文件路径读取Excel（指定sheet编号和表头行数）。
     *
     * <p>内部使用 EasyExcel 的 ReadListener 逐行收集数据，
     * 读取完毕后返回完整列表。
     *
     * @param <T>          数据实体类型
     * @param filePath     Excel文件路径，不能为 {@code null}
     * @param clazz        数据实体类，用于字段映射
     * @param sheetNo      要读取的Sheet编号（从0开始）
     * @param headRowNumber 表头所占行数（用于跳过表头）
     * @return 解析后的实体列表，不会为 {@code null}（无数据时返回空列表）
     */
    public static <T> List<T> read(String filePath, Class<T> clazz, int sheetNo, int headRowNumber) {
        List<T> result = new ArrayList<>();
        EasyExcel.read(filePath, clazz, new ReadListener<T>() {
            @Override
            public void invoke(T data, AnalysisContext context) {
                result.add(data);
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.debug("Excel read complete: {} rows", result.size());
            }
        }).sheet(sheetNo).headRowNumber(headRowNumber).doRead();
        return result;
    }

    // ======================== 读取：输入流 ========================

    /**
     * 从输入流读取Excel（默认读取第一个sheet，表头占1行）。
     *
     * <p>便捷方法，等价于 {@code read(inputStream, clazz, 0, 1)}。
     * 适用于从Web上传、Blob存储等场景获取的输入流。
     *
     * @param <T>         数据实体类型
     * @param inputStream Excel文件的输入流，调用方负责关闭
     * @param clazz       数据实体类，用于字段映射
     * @return 解析后的实体列表，不会为 {@code null}（无数据时返回空列表）
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> clazz) {
        return read(inputStream, clazz, 0, 1);
    }

    /**
     * 从输入流读取Excel（指定sheet编号和表头行数）。
     *
     * <p>与文件路径读取的差异仅在于数据源为输入流。
     *
     * @param <T>          数据实体类型
     * @param inputStream  Excel文件的输入流，调用方负责关闭
     * @param clazz        数据实体类，用于字段映射
     * @param sheetNo      要读取的Sheet编号（从0开始）
     * @param headRowNumber 表头所占行数（用于跳过表头）
     * @return 解析后的实体列表，不会为 {@code null}（无数据时返回空列表）
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> clazz, int sheetNo, int headRowNumber) {
        List<T> result = new ArrayList<>();
        EasyExcel.read(inputStream, clazz, new ReadListener<T>() {
            @Override
            public void invoke(T data, AnalysisContext context) {
                result.add(data);
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.debug("Excel read complete from stream: {} rows", result.size());
            }
        }).sheet(sheetNo).headRowNumber(headRowNumber).doRead();
        return result;
    }

    // ======================== 分批读取 ========================

    /**
     * 分批读取Excel，避免大数据量场景下的内存溢出。
     *
     * <p>每累积达到 {@code batchSize} 条数据时，调用一次 {@code consumer}。
     * 读取完所有数据后，如果剩余不足一批的数据也会被消费。
     *
     * <p><b>典型场景：</b>将百万级Excel数据分批写入数据库
     * <pre>{@code
     * ExcelUtil.batchRead("huge.xlsx", UserDto.class, 500, batch -> {
     *     userMapper.batchInsert(batch);
     * });
     * }</pre>
     *
     * @param <T>       数据实体类型
     * @param filePath  Excel文件路径，不能为 {@code null}
     * @param clazz     数据实体类，用于字段映射
     * @param batchSize 每批处理的数据量，建议 100-1000 之间
     * @param consumer  每批数据的消费回调，在独立线程中同步执行
     */
    public static <T> void batchRead(String filePath, Class<T> clazz, int batchSize, Consumer<List<T>> consumer) {
        EasyExcel.read(filePath, clazz, new ReadListener<T>() {
            private final List<T> batch = new ArrayList<>();
            @Override
            public void invoke(T data, AnalysisContext context) {
                batch.add(data);
                if (batch.size() >= batchSize) {
                    consumer.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 处理剩余不足一批的数据
                if (!batch.isEmpty()) {
                    consumer.accept(new ArrayList<>(batch));
                }
            }
        }).sheet().doRead();
    }

    // ======================== 写入：文件路径 ========================

    /**
     * 将数据列表写入Excel文件。
     *
     * <p>自动注册以下写入处理器：
     * <ul>
     *   <li>{@link StyleTemplate#defaultStyle()} —— 默认表格样式（边框、对齐等）</li>
     *   <li>{@link LongestMatchColumnWidthStyleStrategy} —— 按内容自适应列宽</li>
     * </ul>
     *
     * @param <T>       数据实体类型
     * @param filePath  输出的Excel文件路径，不能为 {@code null}
     * @param sheetName 工作表名称
     * @param data      要写入的数据列表
     * @param clazz     数据实体类，用于字段映射为列
     */
    public static <T> void write(String filePath, String sheetName, List<T> data, Class<T> clazz) {
        EasyExcel.write(filePath, clazz)
                .registerWriteHandler(StyleTemplate.defaultStyle())
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet(sheetName)
                .doWrite(data);
    }

    // ======================== 写入：输出流 ========================

    /**
     * 将数据列表写入输出流（如HTTP响应的 {@link javax.servlet.ServletOutputStream}）。
     *
     * <p>与文件路径写入使用相同的样式和列宽策略。
     * 适用于Web导出场景：直接将Excel数据写入浏览器响应流。
     *
     * <p><b>Web使用示例：</b>
     * <pre>{@code
     * @GetMapping("/export")
     * public void export(HttpServletResponse response) throws IOException {
     *     response.setContentType("application/vnd.ms-excel");
     *     response.setHeader("Content-Disposition", "attachment; filename=export.xlsx");
     *     ExcelUtil.write(response.getOutputStream(), "数据", dataList, DataDto.class);
     * }
     * }</pre>
     *
     * @param <T>          数据实体类型
     * @param outputStream 输出流，调用方负责关闭
     * @param sheetName    工作表名称
     * @param data         要写入的数据列表
     * @param clazz        数据实体类，用于字段映射为列
     */
    public static <T> void write(OutputStream outputStream, String sheetName, List<T> data, Class<T> clazz) {
        EasyExcel.write(outputStream, clazz)
                .registerWriteHandler(StyleTemplate.defaultStyle())
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet(sheetName)
                .doWrite(data);
    }

    // ======================== 读取并校验 ========================

    /**
     * 读取Excel文件并在读取过程中对每一行数据进行校验。
     *
     * <p>结合了读取和校验两个步骤：边读边校验，行级隔离。
     * 每读取一行数据，立即调用 {@link DataValidator#validate} 执行校验，
     * 将所有校验错误收集到 {@link ValidationResult} 中统一返回。
     *
     * <p>行号从第2行开始计数（第1行为表头）。
     *
     * <p><b>使用示例：</b>
     * <pre>{@code
     * ValidationResult result = ExcelUtil.readAndValidate("import.xlsx", UserDto.class);
     * if (result.hasErrors()) {
     *     // 打印所有错误
     *     result.getErrors().forEach(err ->
     *         log.warn("第{}行 字段[{}]: {}", err.getRow(), err.getField(), err.getMessage())
     *     );
     * } else {
     *     // 无错误，继续处理
     * }
     * }</pre>
     *
     * @param <T>      数据实体类型
     * @param filePath Excel文件路径，不能为 {@code null}
     * @param clazz    数据实体类（字段需标注 {@code @ExcelValidation} 注解）
     * @return 校验结果容器；{@link ValidationResult#hasErrors()} 返回 {@code true} 表示存在校验错误
     */
    public static <T> ValidationResult readAndValidate(String filePath, Class<T> clazz) {
        ValidationResult result = new ValidationResult();
        EasyExcel.read(filePath, clazz, new ReadListener<T>() {
            private int rowNum = 1; // 行号从1开始（表头占第1行，数据从第2行开始）
            @Override
            public void invoke(T data, AnalysisContext context) {
                rowNum++;
                ValidationResult rowResult = DataValidator.validate(data, rowNum);
                result.getErrors().addAll(rowResult.getErrors());
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 读取完成，无需额外处理
            }
        }).sheet().doRead();
        return result;
    }
}
