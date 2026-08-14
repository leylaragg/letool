package com.github.leyland.letool.ruleengine.architecture;

import com.github.leyland.letool.ruleengine.compile.DefaultExpressionCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("规则引擎核心依赖边界")
class CoreDependencyBoundaryTest {

    @Test
    @DisplayName("核心编译字节码不应引用宿主、重型运行时或危险能力")
    void shouldNotReferenceForbiddenRuntimePackages() throws Exception {
        assertThat(ClassFileBoundaryScanner.scanCodeSource(DefaultExpressionCompiler.class))
                .isEmpty();
    }

    @Test
    @DisplayName("扫描器应从真实方法引用中精确识别危险能力")
    void shouldDetectExactDangerousMethodReferences() throws Exception {
        List<String> violations = ClassFileBoundaryScanner.scanClassBytes(
                classBytes(DangerousFixture.class), "DangerousFixture.class");

        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/Class#forName"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/reflect/Method#invoke"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/reflect/Method#setAccessible"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/Runtime#getRuntime"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/ProcessBuilder#<init>"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/ProcessBuilder#start"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/System#getenv"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/lang/System#getProperty"));
        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("java/sql/Connection"));
        assertThat(ClassFileBoundaryScanner.scanClassBytes(
                classBytes(SafeFixture.class), "SafeFixture.class")).isEmpty();
    }

    @Test
    @DisplayName("扫描器应支持带空格路径中的 JAR 文件")
    void shouldScanJarOnPathContainingSpaces() throws Exception {
        Path jar = Path.of("target", "boundary test with spaces", "fixtures.jar");
        Files.createDirectories(jar.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            add(output, "fixture/SafeFixture.class", classBytes(SafeFixture.class));
            add(output, "fixture/DangerousFixture.class", classBytes(DangerousFixture.class));
        }

        List<String> violations = ClassFileBoundaryScanner.scan(jar);

        assertThat(violations).anySatisfy(value -> assertThat(value)
                .contains("DangerousFixture.class").contains("java/lang/Class#forName"));
        assertThat(violations).noneSatisfy(value -> assertThat(value)
                .contains("SafeFixture.class"));

        URI jarUri = URI.create("jar:" + jar.toUri() + "!/");
        assertThat(ClassFileBoundaryScanner.scan(jarUri)).anySatisfy(value -> assertThat(value)
                .contains("DangerousFixture.class").contains("java/lang/Class#forName"));
    }

    @Test
    @DisplayName("扫描器应拒绝损坏的类文件而不是静默跳过")
    void shouldRejectMalformedClassFile() {
        assertThatThrownBy(() -> ClassFileBoundaryScanner.scanClassBytes(
                new byte[]{0, 1, 2, 3}, "malformed.class"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("无法解析类文件常量池");
    }

    @Test
    @DisplayName("扫描器应识别字段方法泛型和注解描述符中的禁包类型")
    void shouldDetectForbiddenTypesFromStructuredUtf8Descriptors() {
        assertThat(ClassFileBoundaryScanner.scanStructuredTypeReferences(List.of(
                "Ljava/sql/Connection;",
                "(Ljava/sql/Connection;)Ljava/sql/Connection;",
                "Ljava/util/List<Ljava/sql/Connection;>;",
                "Lcom/github/leyland/letool/ruleengine/autoconfigure/RuleEngineAutoConfiguration;",
                "Lorg/apache/ibatis/session/SqlSession;",
                "Lcom/github/benmanes/caffeine/cache/Cache;",
                "Lorg/apache/ibatis/恶意;",
                "Lcom/github/benmanes/caffeine/cache/Outer.Inner;"), "synthetic.class"))
                .anySatisfy(value -> assertThat(value).contains("java/sql/Connection"))
                .anySatisfy(value -> assertThat(value).contains(
                        "com/github/leyland/letool/ruleengine/autoconfigure/RuleEngineAutoConfiguration"))
                .anySatisfy(value -> assertThat(value).contains("org/apache/ibatis/session/SqlSession"))
                .anySatisfy(value -> assertThat(value)
                        .contains("com/github/benmanes/caffeine/cache/Cache"))
                .anySatisfy(value -> assertThat(value).contains("org/apache/ibatis/恶意"))
                .anySatisfy(value -> assertThat(value)
                        .contains("com/github/benmanes/caffeine/cache/Outer.Inner"));
    }

    @Test
    @DisplayName("扫描器应覆盖稳定的数据库技术命名空间")
    void shouldDetectDatabaseTechnologyDescriptors() {
        List<String> owners = databaseTechnologyOwners();

        assertThat(ClassFileBoundaryScanner.scanStructuredTypeReferences(
                owners.stream().map(owner -> "L" + owner + ";").toList(), "database.class"))
                .containsExactlyInAnyOrderElementsOf(owners.stream()
                        .map(owner -> "database.class -> " + owner).toList());
    }

    @Test
    @DisplayName("扫描器应识别模块描述中的数据库模块")
    void shouldDetectDatabaseModules() throws Exception {
        byte[] moduleInfo = moduleInfoFixture();
        ModuleDescriptor descriptor = ModuleDescriptor.read(ByteBuffer.wrap(moduleInfo));

        assertThat(descriptor.requires()).extracting(ModuleDescriptor.Requires::name)
                .contains("java.sql", "java.transaction.xa");
        assertThat(ClassFileBoundaryScanner.scanClassBytes(
                moduleInfo, "module-info.class"))
                .contains("module-info.class -> module java.sql",
                        "module-info.class -> module java.transaction.xa");
    }

    @Test
    @DisplayName("扫描器应拒绝非法名称类型描述符索引")
    void shouldRejectInvalidNameAndTypeDescriptorIndex() throws Exception {
        assertMalformed(invalidNameAndTypeDescriptorFixture());
    }

    @Test
    @DisplayName("扫描器应拒绝非法方法句柄引用索引")
    void shouldRejectInvalidMethodHandleReferenceIndex() throws Exception {
        assertMalformed(invalidMethodHandleReferenceFixture());
    }

    @Test
    @DisplayName("扫描器应拒绝非法签名属性索引")
    void shouldRejectInvalidSignatureAttributeIndex() throws Exception {
        assertMalformed(invalidSignatureAttributeFixture());
    }

    @Test
    @DisplayName("扫描器应拒绝缺少保留槽的双槽常量")
    void shouldRejectDoubleSlotConstantWithoutReservedSlot() throws Exception {
        assertMalformed(invalidDoubleSlotFixture());
    }

    @Test
    @DisplayName("扫描器应拒绝常量池合法但类主体截断的字节码")
    void shouldRejectClassFileTruncatedAfterConstantPool() {
        byte[] truncated = new byte[]{
                (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe,
                0, 0, 0, 61,
                0, 1
        };

        assertThatThrownBy(() -> ClassFileBoundaryScanner.scanClassBytes(
                truncated, "truncated.class"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("无法解析类文件常量池");
    }

    private static void add(ZipOutputStream output, String name, byte[] bytes) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String name = "/" + type.getName().replace('.', '/') + ".class";
        try (var input = type.getResourceAsStream(name)) {
            if (input == null) throw new IOException("无法读取测试字节码");
            return input.readAllBytes();
        }
    }

    private static List<String> databaseTechnologyOwners() {
        return List.of(
                "javax/sql/DataSource", "javax/persistence/EntityManager",
                "jakarta/persistence/EntityManager", "org/springframework/jdbc/core/JdbcTemplate",
                "org/springframework/data/jdbc/repository/JdbcRepository",
                "org/springframework/data/r2dbc/repository/R2dbcRepository",
                "io/r2dbc/spi/ConnectionFactory", "org/jooq/DSLContext",
                "org/apache/ibatis/session/SqlSession", "com/baomidou/core/mapper/BaseMapper",
                "com/mysql/cj/jdbc/Driver", "org/postgresql/Driver", "oracle/jdbc/OracleDriver",
                "org/mariadb/jdbc/Driver", "com/microsoft/sqlserver/jdbc/SQLServerDriver",
                "org/h2/Driver", "org/hsqldb/jdbc/JDBCDriver",
                "org/apache/derby/jdbc/EmbeddedDriver", "com/zaxxer/hikari/HikariDataSource",
                "com/alibaba/druid/pool/DruidDataSource",
                "org/apache/commons/dbcp2/BasicDataSource",
                "org/apache/tomcat/jdbc/pool/DataSource",
                "com/mchange/v2/c3p0/ComboPooledDataSource");
    }

    private static void assertMalformed(byte[] bytes) {
        assertThatThrownBy(() -> ClassFileBoundaryScanner.scanClassBytes(
                bytes, "malformed.class"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("无法解析类文件常量池");
    }

    private static byte[] invalidNameAndTypeDescriptorFixture() throws IOException {
        return ordinaryClass(7, output -> {
            writeUtf8(output, "method");
            output.writeByte(12);
            output.writeShort(5);
            output.writeShort(2);
        }, output -> output.writeShort(0));
    }

    private static byte[] invalidMethodHandleReferenceFixture() throws IOException {
        return ordinaryClass(6, output -> {
            output.writeByte(15);
            output.writeByte(6);
            output.writeShort(1);
        }, output -> output.writeShort(0));
    }

    private static byte[] invalidSignatureAttributeFixture() throws IOException {
        return ordinaryClass(6, output -> writeUtf8(output, "Signature"), output -> {
            output.writeShort(1);
            output.writeShort(5);
            output.writeInt(2);
            output.writeShort(2);
        });
    }

    private static byte[] invalidDoubleSlotFixture() throws IOException {
        return ordinaryClass(6, output -> {
            output.writeByte(5);
            output.writeLong(0L);
        }, output -> output.writeShort(0));
    }

    private static byte[] ordinaryClass(
            int constantPoolCount, IoWriter extraConstants, IoWriter attributes)
            throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            writeHeader(output, constantPoolCount);
            writeUtf8(output, "fixture/BoundaryFixture");
            writeClass(output, 1);
            writeUtf8(output, "java/lang/Object");
            writeClass(output, 3);
            extraConstants.write(output);
            output.writeShort(0x0020);
            output.writeShort(2);
            output.writeShort(4);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            attributes.write(output);
            return bytes.toByteArray();
        }
    }

    private static byte[] moduleInfoFixture() throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            writeHeader(output, 12);
            writeUtf8(output, "module-info");
            writeClass(output, 1);
            writeUtf8(output, "Module");
            writeUtf8(output, "fixture.boundary");
            writeModule(output, 4);
            writeUtf8(output, "java.base");
            writeModule(output, 6);
            writeUtf8(output, "java.sql");
            writeModule(output, 8);
            writeUtf8(output, "java.transaction.xa");
            writeModule(output, 10);
            output.writeShort(0x8000);
            output.writeShort(2);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(1);
            output.writeShort(3);
            output.writeInt(34);
            output.writeShort(5);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(3);
            writeRequires(output, 7);
            writeRequires(output, 9);
            writeRequires(output, 11);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            return bytes.toByteArray();
        }
    }

    private static void writeHeader(DataOutputStream output, int constantPoolCount)
            throws IOException {
        output.writeInt(0xCAFEBABE);
        output.writeShort(0);
        output.writeShort(61);
        output.writeShort(constantPoolCount);
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        output.writeByte(1);
        output.writeUTF(value);
    }

    private static void writeClass(DataOutputStream output, int nameIndex) throws IOException {
        output.writeByte(7);
        output.writeShort(nameIndex);
    }

    private static void writeModule(DataOutputStream output, int nameIndex) throws IOException {
        output.writeByte(19);
        output.writeShort(nameIndex);
    }

    private static void writeRequires(DataOutputStream output, int moduleIndex)
            throws IOException {
        output.writeShort(moduleIndex);
        output.writeShort(0);
        output.writeShort(0);
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private static final class SafeFixture {
        static String value() { return "safe"; }
    }

    private static final class DangerousFixture {
        static Object classLookup(String name) throws Exception { return Class.forName(name); }
        static Object invoke(Method method, Object target) throws Exception {
            method.setAccessible(true);
            return method.invoke(target);
        }
        static Runtime runtime() { return Runtime.getRuntime(); }
        static Process process() throws IOException { return new ProcessBuilder("safe").start(); }
        static String environment(String key) { return System.getenv(key); }
        static String property(String key) { return System.getProperty(key); }
        static Class<?> forbiddenDatabaseType() { return java.sql.Connection.class; }
    }
}
