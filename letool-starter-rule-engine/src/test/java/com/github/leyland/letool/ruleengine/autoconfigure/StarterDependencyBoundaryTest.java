package com.github.leyland.letool.ruleengine.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("规则引擎 Starter 数据库依赖边界")
class StarterDependencyBoundaryTest {

    @Test
    @DisplayName("Starter 生产字节码不应引用数据库基础设施")
    void shouldNotReferenceDatabaseInfrastructure() {
        assertThat(StarterClassFileBoundaryScanner.scanCodeSource(
                RuleEngineAutoConfiguration.class)).isEmpty();
    }

    @Test
    @DisplayName("扫描器应从描述符中识别 DataSource 与 MyBatis 类型")
    void shouldDetectDatabaseTypesFromDescriptors() throws Exception {
        List<String> fixtureViolations = StarterClassFileBoundaryScanner.scanClassBytes(
                classBytes(DataSourceDescriptorFixture.class),
                "DataSourceDescriptorFixture.class");

        assertThat(fixtureViolations).anySatisfy(value -> assertThat(value)
                .contains("javax/sql/DataSource"));
        assertThat(StarterClassFileBoundaryScanner.scanStructuredTypeReferences(List.of(
                "(Lorg/apache/ibatis/session/SqlSession;)Ljava/lang/Object;",
                "Ljakarta/persistence/EntityManager;"),
                "synthetic.class")).anySatisfy(value -> assertThat(value)
                .contains("org/apache/ibatis/session/SqlSession"))
                .anySatisfy(value -> assertThat(value)
                        .contains("jakarta/persistence/EntityManager"));
    }

    @Test
    @DisplayName("扫描器应覆盖稳定的数据库技术命名空间")
    void shouldDetectDatabaseTechnologyDescriptors() {
        List<String> owners = databaseTechnologyOwners();

        assertThat(StarterClassFileBoundaryScanner.scanStructuredTypeReferences(
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
        assertThat(StarterClassFileBoundaryScanner.scanClassBytes(
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
    @DisplayName("扫描器应支持目录、JAR 路径和 JAR URI")
    void shouldScanDirectoryAndJarLocations() throws Exception {
        Path fixtureRoot = Path.of("target", "starter boundary test");
        Path classes = fixtureRoot.resolve("classes");
        Path fixture = classes.resolve("fixture/DataSourceDescriptorFixture.class");
        Files.createDirectories(fixture.getParent());
        Files.write(fixture, classBytes(DataSourceDescriptorFixture.class));

        assertThat(StarterClassFileBoundaryScanner.scan(classes))
                .anySatisfy(value -> assertThat(value).contains("javax/sql/DataSource"));

        Path jar = fixtureRoot.resolve("boundary fixtures.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            add(output, "fixture/DataSourceDescriptorFixture.class",
                    classBytes(DataSourceDescriptorFixture.class));
        }
        assertThat(StarterClassFileBoundaryScanner.scan(jar))
                .anySatisfy(value -> assertThat(value).contains("javax/sql/DataSource"));

        URI jarUri = URI.create("jar:" + jar.toUri() + "!/");
        assertThat(StarterClassFileBoundaryScanner.scan(jarUri))
                .anySatisfy(value -> assertThat(value).contains("javax/sql/DataSource"));
    }

    @Test
    @DisplayName("扫描器应拒绝常量池后截断的类文件")
    void shouldRejectClassFileTruncatedAfterConstantPool() {
        byte[] truncated = new byte[]{
                (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe,
                0, 0, 0, 61,
                0, 1
        };

        assertThatThrownBy(() -> StarterClassFileBoundaryScanner.scanClassBytes(
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
        assertThatThrownBy(() -> StarterClassFileBoundaryScanner.scanClassBytes(
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

    /** 只在描述符中携带数据库类型，避免生成类字面量引用。 */
    private static final class DataSourceDescriptorFixture {
        private DataSource dataSource;

        DataSource exchange(DataSource candidate) {
            return candidate;
        }
    }
}
