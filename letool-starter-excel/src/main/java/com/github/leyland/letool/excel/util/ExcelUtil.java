package com.github.leyland.letool.excel.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.PageReadListener;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.github.leyland.letool.excel.exception.ExcelException;
import com.github.leyland.letool.excel.style.StyleTemplate;
import com.github.leyland.letool.excel.validation.DataValidator;
import com.github.leyland.letool.excel.validation.ValidationResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 基于 EasyExcel 原生能力的轻量读写工具。
 *
 * <p>实体字段映射、日期与数字格式、类型转换器等能力直接使用 EasyExcel 的
 * 原生注解和扩展接口。本工具仅负责常用调用链、参数边界、默认样式、
 * 分批回调、Letool 数据校验和统一异常转换，不再维护重复的字段映射体系。</p>
 *
 * <p>接收调用方创建的输入流或输出流时不会主动关闭流，流的生命周期仍由调用方管理。
 * 文件路径重载创建的底层流则由 EasyExcel 正常关闭。</p>
 */
public final class ExcelUtil {

    /**
     * 禁止实例化静态工具类。
     */
    private ExcelUtil() {
    }

    /**
     * 从文件读取第一个工作表，默认表头占一行。
     *
     * @param filePath Excel 文件路径，不允许为空白
     * @param clazz 行数据类型，不允许为 {@code null}
     * @param <T> 行数据类型
     * @return 读取到的全部数据行
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当文件读取或解析失败时抛出
     */
    public static <T> List<T> read(String filePath, Class<T> clazz) {
        return read(filePath, clazz, 0, 1);
    }

    /**
     * 从文件读取指定工作表。
     *
     * <p>该方法会将全部数据加载到内存，仅适合数据量可控的场景。
     * 大文件应使用 {@link #batchRead(String, Class, int, Consumer)}。</p>
     *
     * @param filePath Excel 文件路径，不允许为空白
     * @param clazz 行数据类型，不允许为 {@code null}
     * @param sheetNo 工作表下标，从 0 开始
     * @param headRowNumber 表头行数，不允许为负数
     * @param <T> 行数据类型
     * @return 读取到的全部数据行
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当文件读取或解析失败时抛出
     */
    public static <T> List<T> read(
            String filePath,
            Class<T> clazz,
            int sheetNo,
            int headRowNumber) {
        requireFilePath(filePath);
        requireClass(clazz);
        requireReadPosition(sheetNo, headRowNumber);
        List<T> rows = new ArrayList<>();
        try {
            EasyExcel.read(filePath, clazz, collectingListener(rows))
                    .sheet(sheetNo)
                    .headRowNumber(headRowNumber)
                    .doRead();
            return rows;
        } catch (RuntimeException exception) {
            throw toReadException(exception);
        }
    }

    /**
     * 从输入流读取第一个工作表，默认表头占一行。
     *
     * @param inputStream Excel 输入流，由调用方负责关闭
     * @param clazz 行数据类型，不允许为 {@code null}
     * @param <T> 行数据类型
     * @return 读取到的全部数据行
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当工作簿读取或解析失败时抛出
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> clazz) {
        return read(inputStream, clazz, 0, 1);
    }

    /**
     * 从输入流读取指定工作表。
     *
     * <p>该方法不会关闭调用方传入的输入流，并会将全部数据加载到内存。</p>
     *
     * @param inputStream Excel 输入流，由调用方负责关闭
     * @param clazz 行数据类型，不允许为 {@code null}
     * @param sheetNo 工作表下标，从 0 开始
     * @param headRowNumber 表头行数，不允许为负数
     * @param <T> 行数据类型
     * @return 读取到的全部数据行
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当工作簿读取或解析失败时抛出
     */
    public static <T> List<T> read(
            InputStream inputStream,
            Class<T> clazz,
            int sheetNo,
            int headRowNumber) {
        requireInputStream(inputStream);
        requireClass(clazz);
        requireReadPosition(sheetNo, headRowNumber);
        List<T> rows = new ArrayList<>();
        try {
            EasyExcel.read(inputStream, clazz, collectingListener(rows))
                    .autoCloseStream(false)
                    .sheet(sheetNo)
                    .headRowNumber(headRowNumber)
                    .doRead();
            return rows;
        } catch (RuntimeException exception) {
            throw toReadException(exception);
        }
    }

    /**
     * 按固定批次读取第一个工作表。
     *
     * <p>每批列表都是不可修改快照，回调执行结束后不会被工具类复用。
     * 回调在当前读取线程中同步执行；回调自身抛出的运行时异常会原样传播。</p>
     *
     * @param filePath Excel 文件路径，不允许为空白
     * @param clazz 行数据类型，不允许为 {@code null}
     * @param batchSize 每批数据量，必须大于 0
     * @param consumer 批次消费回调，不允许为 {@code null}
     * @param <T> 行数据类型
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当文件读取或解析失败时抛出
     */
    public static <T> void batchRead(
            String filePath,
            Class<T> clazz,
            int batchSize,
            Consumer<List<T>> consumer) {
        requireFilePath(filePath);
        requireClass(clazz);
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }

        Consumer<List<T>> guardedConsumer = batch -> {
            try {
                consumer.accept(List.copyOf(batch));
            } catch (RuntimeException exception) {
                throw new BatchConsumerException(exception);
            }
        };
        try {
            EasyExcel.read(
                            filePath,
                            clazz,
                            new PageReadListener<>(guardedConsumer, batchSize)
                    )
                    .sheet()
                    .doRead();
        } catch (RuntimeException exception) {
            RuntimeException consumerFailure = findBatchConsumerFailure(exception);
            if (consumerFailure != null) {
                throw consumerFailure;
            }
            throw toReadException(exception);
        }
    }

    /**
     * 将数据写入文件。
     *
     * <p>写入过程默认应用 Letool 表格样式和最长内容列宽策略。
     * 字段映射、格式与转换器由 EasyExcel 原生元数据控制。</p>
     *
     * @param filePath 输出文件路径，不允许为空白
     * @param sheetName 工作表名称，不允许为空白
     * @param data 待写入数据，不允许为 {@code null}
     * @param clazz 行数据类型，不允许为 {@code null}
     * @param <T> 行数据类型
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当工作簿生成或写出失败时抛出
     */
    public static <T> void write(
            String filePath,
            String sheetName,
            List<T> data,
            Class<T> clazz) {
        requireFilePath(filePath);
        requireSheetName(sheetName);
        requireData(data);
        requireClass(clazz);
        try {
            EasyExcel.write(filePath, clazz)
                    .registerWriteHandler(StyleTemplate.defaultStyle())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .doWrite(data);
        } catch (RuntimeException exception) {
            throw toWriteException(exception);
        }
    }

    /**
     * 将数据写入调用方提供的输出流。
     *
     * <p>写入完成后不会关闭输出流，调用方可以继续使用或自行关闭。</p>
     *
     * @param outputStream Excel 输出流，由调用方负责关闭
     * @param sheetName 工作表名称，不允许为空白
     * @param data 待写入数据，不允许为 {@code null}
     * @param clazz 行数据类型，不允许为 {@code null}
     * @param <T> 行数据类型
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当工作簿生成或写出失败时抛出
     */
    public static <T> void write(
            OutputStream outputStream,
            String sheetName,
            List<T> data,
            Class<T> clazz) {
        requireOutputStream(outputStream);
        requireSheetName(sheetName);
        requireData(data);
        requireClass(clazz);
        try {
            EasyExcel.write(outputStream, clazz)
                    .autoCloseStream(false)
                    .registerWriteHandler(StyleTemplate.defaultStyle())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .doWrite(data);
        } catch (RuntimeException exception) {
            throw toWriteException(exception);
        }
    }

    /**
     * 读取第一个工作表并逐行执行 Letool 数据校验。
     *
     * <p>结果中的行号来自 EasyExcel 读取上下文，因此会正确包含表头占用的行数。
     * 普通规则不通过会被收集并继续读取；规则无法执行时会抛出校验异常。</p>
     *
     * @param filePath Excel 文件路径，不允许为空白
     * @param clazz 带有校验注解的行数据类型
     * @param <T> 行数据类型
     * @return 全部数据行的校验汇总
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @throws ExcelException 当读取失败或校验规则无法执行时抛出
     */
    public static <T> ValidationResult readAndValidate(String filePath, Class<T> clazz) {
        requireFilePath(filePath);
        requireClass(clazz);
        ValidationResult result = new ValidationResult();
        ReadListener<T> listener = new ReadListener<>() {
            /**
             * 校验当前数据行，并将结果合并到汇总对象。
             *
             * @param data 当前数据行
             * @param context EasyExcel 读取上下文
             */
            @Override
            public void invoke(T data, AnalysisContext context) {
                int rowNumber = context.readRowHolder().getRowIndex() + 1;
                result.merge(DataValidator.validate(data, rowNumber));
            }

            /**
             * 全部数据读取完成后无需执行额外处理。
             *
             * @param context EasyExcel 读取上下文
             */
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 汇总结果已在逐行回调中完成。
            }
        };

        try {
            EasyExcel.read(filePath, clazz, listener)
                    .sheet()
                    .doRead();
            return result;
        } catch (RuntimeException exception) {
            throw toReadException(exception);
        }
    }

    /**
     * 创建把每行数据收集到目标列表的监听器。
     *
     * @param rows 目标数据列表
     * @param <T> 行数据类型
     * @return EasyExcel 行读取监听器
     */
    private static <T> ReadListener<T> collectingListener(List<T> rows) {
        return new ReadListener<>() {
            /**
             * 收集当前数据行。
             *
             * @param data 当前数据行
             * @param context EasyExcel 读取上下文
             */
            @Override
            public void invoke(T data, AnalysisContext context) {
                rows.add(data);
            }

            /**
             * 全部数据读取完成后无需执行额外处理。
             *
             * @param context EasyExcel 读取上下文
             */
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 数据已在逐行回调中收集。
            }
        };
    }

    /**
     * 将读取阶段异常转换为统一异常，并保留已经存在的 Excel 异常。
     *
     * @param exception 读取阶段抛出的运行时异常
     * @return 可直接抛出的统一异常
     */
    private static ExcelException toReadException(RuntimeException exception) {
        ExcelException existing = findExcelException(exception);
        return existing == null ? ExcelException.readFailed(exception) : existing;
    }

    /**
     * 将写入阶段异常转换为统一异常，并保留已经存在的 Excel 异常。
     *
     * @param exception 写入阶段抛出的运行时异常
     * @return 可直接抛出的统一异常
     */
    private static ExcelException toWriteException(RuntimeException exception) {
        ExcelException existing = findExcelException(exception);
        return existing == null ? ExcelException.writeFailed(exception) : existing;
    }

    /**
     * 在异常原因链中查找已转换的 Excel 异常。
     *
     * @param exception 异常链入口
     * @return 找到的 Excel 异常；不存在时返回 {@code null}
     */
    private static ExcelException findExcelException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ExcelException excelException) {
                return excelException;
            }
            if (current == current.getCause()) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 从 EasyExcel 异常链中恢复用户批次回调抛出的原始异常。
     *
     * @param exception EasyExcel 传播出的异常
     * @return 用户回调原始异常；不存在时返回 {@code null}
     */
    private static RuntimeException findBatchConsumerFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof BatchConsumerException
                    && current.getCause() instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            if (current == current.getCause()) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 校验文件路径。
     *
     * @param filePath 待校验文件路径
     */
    private static void requireFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be blank");
        }
    }

    /**
     * 校验输入流。
     *
     * @param inputStream 待校验输入流
     */
    private static void requireInputStream(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream must not be null");
        }
    }

    /**
     * 校验输出流。
     *
     * @param outputStream 待校验输出流
     */
    private static void requireOutputStream(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream must not be null");
        }
    }

    /**
     * 校验工作表名称。
     *
     * @param sheetName 待校验工作表名称
     */
    private static void requireSheetName(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException("sheetName must not be blank");
        }
    }

    /**
     * 校验待写入数据。
     *
     * @param data 待校验数据列表
     */
    private static void requireData(List<?> data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
    }

    /**
     * 校验行数据类型。
     *
     * @param clazz 待校验类型
     */
    private static void requireClass(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz must not be null");
        }
    }

    /**
     * 校验读取位置参数。
     *
     * @param sheetNo 工作表下标
     * @param headRowNumber 表头行数
     */
    private static void requireReadPosition(int sheetNo, int headRowNumber) {
        if (sheetNo < 0) {
            throw new IllegalArgumentException("sheetNo must not be negative");
        }
        if (headRowNumber < 0) {
            throw new IllegalArgumentException("headRowNumber must not be negative");
        }
    }

    /**
     * 标记批次消费回调抛出的异常，避免被误转换为读取失败。
     */
    private static final class BatchConsumerException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /**
         * 包装用户回调异常。
         *
         * @param cause 用户回调抛出的运行时异常
         */
        private BatchConsumerException(RuntimeException cause) {
            super(cause);
        }
    }
}
