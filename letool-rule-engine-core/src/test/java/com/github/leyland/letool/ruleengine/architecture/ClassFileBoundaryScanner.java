package com.github.leyland.letool.ruleengine.architecture;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析生产类文件常量池并检查核心依赖边界的测试辅助器。
 */
final class ClassFileBoundaryScanner {

    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final List<String> FORBIDDEN_CLASS_PREFIXES = List.of(
            "org/springframework/", "com/yomahub/", "java/sql/", "javax/sql/",
            "javax/persistence/", "jakarta/persistence/", "javax/script/",
            "groovy/", "org/codehaus/groovy/", "org/apache/ibatis/", "com/baomidou/",
            "org/springframework/data/jdbc/", "org/springframework/data/r2dbc/",
            "io/r2dbc/", "org/jooq/", "com/mysql/", "org/postgresql/", "oracle/jdbc/",
            "org/mariadb/", "com/microsoft/sqlserver/", "org/h2/", "org/hsqldb/",
            "org/apache/derby/", "com/zaxxer/hikari/", "com/alibaba/druid/",
            "org/apache/commons/dbcp2/", "org/apache/tomcat/jdbc/pool/",
            "com/mchange/v2/c3p0/",
            "io/lettuce/", "redis/clients/", "com/github/benmanes/caffeine/", "com/ailind/",
            "com/github/leyland/letool/rule/",
            "com/github/leyland/letool/ruleengine/autoconfigure/");
    private static final List<String> FORBIDDEN_MODULE_PREFIXES = List.of(
            "java.sql", "java.transaction", "javax.persistence", "javax.transaction",
            "jakarta.persistence", "jakarta.transaction", "io.r2dbc", "org.jooq",
            "org.mybatis");

    private ClassFileBoundaryScanner() { }

    static List<String> scanCodeSource(Class<?> anchor) {
        if (anchor == null) throw new IllegalArgumentException("anchor");
        try {
            URI location = anchor.getProtectionDomain().getCodeSource().getLocation().toURI();
            if ("jar".equalsIgnoreCase(location.getScheme())) {
                try (FileSystem fileSystem = FileSystems.newFileSystem(location, Map.of())) {
                    return scanDirectory(fileSystem.getPath("/"));
                }
            }
            return scan(Path.of(location));
        } catch (Exception exception) {
            throw new IllegalStateException("无法扫描核心代码来源", exception);
        }
    }

    static List<String> scan(Path location) {
        if (location == null) throw new IllegalArgumentException("location");
        try {
            if (Files.isDirectory(location)) return scanDirectory(location);
            URI jar = URI.create("jar:" + location.toUri());
            try (FileSystem fileSystem = FileSystems.newFileSystem(jar, Map.of())) {
                return scanDirectory(fileSystem.getPath("/"));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法扫描类目录或 JAR", exception);
        }
    }

    static List<String> scan(URI location) {
        if (location == null) throw new IllegalArgumentException("location");
        if ("file".equalsIgnoreCase(location.getScheme())) return scan(Path.of(location));
        if (!"jar".equalsIgnoreCase(location.getScheme())) {
            throw new IllegalArgumentException("不支持的代码来源协议");
        }
        try {
            try (FileSystem fileSystem = FileSystems.newFileSystem(location, Map.of())) {
                return scanDirectory(fileSystem.getPath("/"));
            }
        } catch (FileSystemAlreadyExistsException exception) {
            try {
                return scanDirectory(FileSystems.getFileSystem(location).getPath("/"));
            } catch (IOException ioException) {
                throw new IllegalStateException("无法扫描已打开的 JAR", ioException);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法扫描代码来源 URI", exception);
        }
    }

    private static List<String> scanDirectory(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                String displayName = root.relativize(classFile).toString().replace('\\', '/');
                violations.addAll(scanClassBytes(Files.readAllBytes(classFile), displayName));
            }
        }
        return List.copyOf(violations);
    }

    static List<String> scanClassBytes(byte[] bytes, String displayName) {
        if (bytes == null || displayName == null) throw new IllegalArgumentException("class bytes");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != CLASS_FILE_MAGIC) throw new IOException("非法类文件魔数");
            input.readUnsignedShort();
            input.readUnsignedShort();
            ConstantPool pool = ConstantPool.read(input);
            pool.validateClassStructure(input);
            if (input.available() != 0) throw new IOException("类文件包含尾随字节");
            return pool.violations(displayName);
        } catch (IOException exception) {
            throw new IllegalStateException("无法解析类文件常量池", exception);
        }
    }

    static List<String> scanStructuredTypeReferences(
            List<String> utf8Values, String displayName) {
        if (utf8Values == null || displayName == null) {
            throw new IllegalArgumentException("structured type references");
        }
        List<String> violations = new ArrayList<>();
        for (String value : utf8Values) {
            if (value == null) throw new IllegalArgumentException("utf8 value");
            int position = 0;
            while ((position = value.indexOf('L', position)) >= 0) {
                if (!isSignatureTypeStart(value, position)) {
                    position++;
                    continue;
                }
                int start = position + 1;
                int end = start;
                while (end < value.length() && value.charAt(end) != ';'
                        && value.charAt(end) != '<') end++;
                if (end < value.length() && end > start) {
                    String owner = value.substring(start, end);
                    if (owner.indexOf('/') >= 0 && isForbiddenOwner(owner)) {
                        violations.add(displayName + " -> " + owner);
                    }
                }
                position = start;
            }
        }
        return List.copyOf(violations);
    }

    private static boolean isSignatureTypeStart(String value, int position) {
        if (position == 0) return true;
        return "[();<:,+-*".indexOf(value.charAt(position - 1)) >= 0;
    }

    private static boolean isForbiddenOwner(String owner) {
        return FORBIDDEN_CLASS_PREFIXES.stream().anyMatch(owner::startsWith);
    }

    private static boolean isForbiddenModule(String module) {
        return FORBIDDEN_MODULE_PREFIXES.stream()
                .anyMatch(prefix -> module.equals(prefix) || module.startsWith(prefix + "."));
    }

    private static final class ConstantPool {
        private final Entry[] entries;

        private ConstantPool(Entry[] entries) {
            this.entries = entries;
        }

        static ConstantPool read(DataInputStream input) throws IOException {
            int count = input.readUnsignedShort();
            if (count == 0) throw new IOException("常量池数量非法");
            Entry[] entries = new Entry[count];
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> entries[index] = new Utf8Entry(input.readUTF());
                    case 7 -> entries[index] = new ClassEntry(input.readUnsignedShort());
                    case 9, 10, 11 -> entries[index] = new MemberEntry(
                            tag, input.readUnsignedShort(), input.readUnsignedShort());
                    case 12 -> entries[index] = new NameAndTypeEntry(
                            input.readUnsignedShort(), input.readUnsignedShort());
                    case 3, 4 -> {
                        input.readInt();
                        entries[index] = new LiteralEntry(tag);
                    }
                    case 5, 6 -> {
                        if (index + 1 >= count) throw new IOException("双槽常量缺少保留槽");
                        input.readLong();
                        entries[index] = new LiteralEntry(tag);
                        entries[++index] = ReservedEntry.INSTANCE;
                    }
                    case 8, 16, 20 -> entries[index] = new IndexEntry(
                            tag, input.readUnsignedShort());
                    case 19 -> entries[index] = new ModuleEntry(input.readUnsignedShort());
                    case 15 -> entries[index] = new MethodHandleEntry(
                            input.readUnsignedByte(), input.readUnsignedShort());
                    case 17, 18 -> entries[index] = new DynamicEntry(
                            tag, input.readUnsignedShort(), input.readUnsignedShort());
                    default -> throw new IOException("未知常量池标签: " + tag);
                }
            }
            ConstantPool pool = new ConstantPool(entries);
            pool.validateReferences();
            return pool;
        }

        private void validateReferences() throws IOException {
            for (Entry entry : entries) {
                if (entry instanceof ClassEntry classEntry) {
                    requireUtf8Index(classEntry.nameIndex);
                } else if (entry instanceof MemberEntry member) {
                    requireClassIndex(member.classIndex);
                    requireNameAndTypeIndex(member.nameAndTypeIndex);
                } else if (entry instanceof NameAndTypeEntry nameAndType) {
                    requireUtf8Index(nameAndType.nameIndex);
                    requireUtf8Index(nameAndType.descriptorIndex);
                } else if (entry instanceof IndexEntry indexed) {
                    requireUtf8Index(indexed.index);
                } else if (entry instanceof ModuleEntry module) {
                    requireUtf8Index(module.nameIndex);
                } else if (entry instanceof MethodHandleEntry handle) {
                    validateMethodHandle(handle);
                } else if (entry instanceof DynamicEntry dynamic) {
                    requireNameAndTypeIndex(dynamic.nameAndTypeIndex);
                }
            }
        }

        private void validateMethodHandle(MethodHandleEntry handle) throws IOException {
            Entry reference = requireEntry(handle.referenceIndex);
            boolean valid = switch (handle.referenceKind) {
                case 1, 2, 3, 4 -> reference instanceof MemberEntry member && member.tag == 9;
                case 5, 8 -> reference instanceof MemberEntry member && member.tag == 10;
                case 6, 7 -> reference instanceof MemberEntry member
                        && (member.tag == 10 || member.tag == 11);
                case 9 -> reference instanceof MemberEntry member && member.tag == 11;
                default -> false;
            };
            if (!valid) throw new IOException("非法方法句柄引用");
        }

        List<String> violations(String displayName) {
            List<String> violations = new ArrayList<>();
            List<String> utf8Values = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry instanceof Utf8Entry utf8Entry) utf8Values.add(utf8Entry.value);
                if (entry instanceof ClassEntry classEntry) {
                    String owner = utf8(classEntry.nameIndex);
                    if (isForbiddenOwner(owner)) violations.add(displayName + " -> " + owner);
                } else if (entry instanceof ModuleEntry moduleEntry) {
                    String module = utf8(moduleEntry.nameIndex);
                    if (isForbiddenModule(module)) {
                        violations.add(displayName + " -> module " + module);
                    }
                } else if (entry instanceof MemberEntry member && member.isMethod()) {
                    String owner = className(member.classIndex);
                    NameAndTypeEntry nameAndType = nameAndType(member.nameAndTypeIndex);
                    String method = utf8(nameAndType.nameIndex);
                    if (isDangerous(owner, method)) {
                        violations.add(displayName + " -> " + owner + "#" + method);
                    }
                }
            }
            violations.addAll(scanStructuredTypeReferences(utf8Values, displayName));
            return List.copyOf(violations);
        }

        void validateClassStructure(DataInputStream input) throws IOException {
            input.readUnsignedShort();
            int thisClass = input.readUnsignedShort();
            int superClass = input.readUnsignedShort();
            requireClassIndex(thisClass);
            if (superClass != 0) requireClassIndex(superClass);
            int interfaces = input.readUnsignedShort();
            for (int index = 0; index < interfaces; index++) {
                requireClassIndex(input.readUnsignedShort());
            }
            validateMembers(input);
            validateMembers(input);
            validateAttributes(input);
        }

        private void validateMembers(DataInputStream input) throws IOException {
            int count = input.readUnsignedShort();
            for (int index = 0; index < count; index++) {
                input.readUnsignedShort();
                requireUtf8Index(input.readUnsignedShort());
                requireUtf8Index(input.readUnsignedShort());
                validateAttributes(input);
            }
        }

        private void validateAttributes(DataInputStream input) throws IOException {
            int count = input.readUnsignedShort();
            for (int index = 0; index < count; index++) {
                int nameIndex = input.readUnsignedShort();
                requireUtf8Index(nameIndex);
                long length = Integer.toUnsignedLong(input.readInt());
                if (length > input.available()) throw new IOException("类文件属性长度越界");
                byte[] bytes = input.readNBytes((int) length);
                try (DataInputStream attribute = new DataInputStream(
                        new ByteArrayInputStream(bytes))) {
                    String name = utf8(nameIndex);
                    if (name.equals("Signature")) validateSignatureAttribute(attribute, length);
                    if (name.equals("Module")) validateModuleAttribute(attribute);
                    if (attribute.available() != 0 && (name.equals("Signature")
                            || name.equals("Module"))) {
                        throw new IOException("类文件属性包含尾随字节");
                    }
                }
            }
        }

        private void validateSignatureAttribute(DataInputStream attribute, long length)
                throws IOException {
            if (length != 2) throw new IOException("签名属性长度非法");
            requireUtf8Index(attribute.readUnsignedShort());
        }

        private void validateModuleAttribute(DataInputStream attribute) throws IOException {
            requireModuleIndex(attribute.readUnsignedShort());
            attribute.readUnsignedShort();
            requireOptionalUtf8Index(attribute.readUnsignedShort());
            int requires = attribute.readUnsignedShort();
            for (int index = 0; index < requires; index++) {
                requireModuleIndex(attribute.readUnsignedShort());
                attribute.readUnsignedShort();
                requireOptionalUtf8Index(attribute.readUnsignedShort());
            }
            validateModuleExportsOrOpens(attribute);
            validateModuleExportsOrOpens(attribute);
            int uses = attribute.readUnsignedShort();
            for (int index = 0; index < uses; index++) {
                requireClassIndex(attribute.readUnsignedShort());
            }
            int provides = attribute.readUnsignedShort();
            for (int index = 0; index < provides; index++) {
                requireClassIndex(attribute.readUnsignedShort());
                int implementations = attribute.readUnsignedShort();
                if (implementations == 0) throw new IOException("模块服务实现为空");
                for (int target = 0; target < implementations; target++) {
                    requireClassIndex(attribute.readUnsignedShort());
                }
            }
        }

        private void validateModuleExportsOrOpens(DataInputStream attribute) throws IOException {
            int count = attribute.readUnsignedShort();
            for (int index = 0; index < count; index++) {
                requirePackageIndex(attribute.readUnsignedShort());
                attribute.readUnsignedShort();
                int targets = attribute.readUnsignedShort();
                for (int target = 0; target < targets; target++) {
                    requireModuleIndex(attribute.readUnsignedShort());
                }
            }
        }

        private void requireOptionalUtf8Index(int index) throws IOException {
            if (index != 0) requireUtf8Index(index);
        }

        private void requireNameAndTypeIndex(int index) throws IOException {
            if (!(requireEntry(index) instanceof NameAndTypeEntry)) {
                throw new IOException("非法名称类型常量索引");
            }
        }

        private void requireModuleIndex(int index) throws IOException {
            if (!(requireEntry(index) instanceof ModuleEntry)) {
                throw new IOException("非法模块常量索引");
            }
        }

        private void requirePackageIndex(int index) throws IOException {
            Entry entry = requireEntry(index);
            if (!(entry instanceof IndexEntry indexed) || indexed.tag != 20) {
                throw new IOException("非法包常量索引");
            }
        }

        private Entry requireEntry(int index) throws IOException {
            if (index <= 0 || index >= entries.length || entries[index] == null
                    || entries[index] instanceof ReservedEntry) {
                throw new IOException("非法常量池索引");
            }
            return entries[index];
        }

        private void requireClassIndex(int index) throws IOException {
            if (!(requireEntry(index) instanceof ClassEntry)) {
                throw new IOException("非法类常量索引");
            }
        }

        private void requireUtf8Index(int index) throws IOException {
            if (!(requireEntry(index) instanceof Utf8Entry)) {
                throw new IOException("非法 UTF8 常量索引");
            }
        }

        private boolean isDangerous(String owner, String method) {
            return owner.equals("java/lang/Class") && method.equals("forName")
                    || (owner.equals("java/lang/reflect/AccessibleObject")
                    || owner.equals("java/lang/reflect/Method")) && method.equals("setAccessible")
                    || owner.equals("java/lang/reflect/Method") && method.equals("invoke")
                    || owner.equals("java/lang/Runtime") && method.equals("getRuntime")
                    || owner.equals("java/lang/ProcessBuilder")
                    && (method.equals("<init>") || method.equals("start"))
                    || owner.equals("java/lang/System")
                    && (method.equals("getenv") || method.equals("getProperty"));
        }

        private String className(int index) {
            Entry entry = require(index);
            if (!(entry instanceof ClassEntry classEntry)) throw invalidEntry();
            return utf8(classEntry.nameIndex);
        }

        private NameAndTypeEntry nameAndType(int index) {
            Entry entry = require(index);
            if (!(entry instanceof NameAndTypeEntry nameAndType)) throw invalidEntry();
            return nameAndType;
        }

        private String utf8(int index) {
            Entry entry = require(index);
            if (!(entry instanceof Utf8Entry utf8)) throw invalidEntry();
            return utf8.value;
        }

        private Entry require(int index) {
            if (index <= 0 || index >= entries.length || entries[index] == null) {
                throw invalidEntry();
            }
            return entries[index];
        }

        private IllegalStateException invalidEntry() {
            return new IllegalStateException("类文件常量池引用非法");
        }
    }

    private sealed interface Entry permits Utf8Entry, ClassEntry, MemberEntry,
            NameAndTypeEntry, IndexEntry, ModuleEntry, MethodHandleEntry,
            DynamicEntry, LiteralEntry, ReservedEntry { }

    private static final class Utf8Entry implements Entry {
        private final String value;
        private Utf8Entry(String value) { this.value = value; }
    }

    private static final class ClassEntry implements Entry {
        private final int nameIndex;
        private ClassEntry(int nameIndex) { this.nameIndex = nameIndex; }
    }

    private static final class MemberEntry implements Entry {
        private final int tag;
        private final int classIndex;
        private final int nameAndTypeIndex;
        private MemberEntry(int tag, int classIndex, int nameAndTypeIndex) {
            this.tag = tag;
            this.classIndex = classIndex;
            this.nameAndTypeIndex = nameAndTypeIndex;
        }
        private boolean isMethod() { return tag == 10 || tag == 11; }
    }

    private static final class NameAndTypeEntry implements Entry {
        private final int nameIndex;
        private final int descriptorIndex;
        private NameAndTypeEntry(int nameIndex, int descriptorIndex) {
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
        }
    }

    private static final class IndexEntry implements Entry {
        private final int tag;
        private final int index;
        private IndexEntry(int tag, int index) {
            this.tag = tag;
            this.index = index;
        }
    }

    private static final class ModuleEntry implements Entry {
        private final int nameIndex;
        private ModuleEntry(int nameIndex) { this.nameIndex = nameIndex; }
    }

    private static final class MethodHandleEntry implements Entry {
        private final int referenceKind;
        private final int referenceIndex;
        private MethodHandleEntry(int referenceKind, int referenceIndex) {
            this.referenceKind = referenceKind;
            this.referenceIndex = referenceIndex;
        }
    }

    private static final class DynamicEntry implements Entry {
        private final int tag;
        private final int bootstrapMethodIndex;
        private final int nameAndTypeIndex;
        private DynamicEntry(int tag, int bootstrapMethodIndex, int nameAndTypeIndex) {
            this.tag = tag;
            this.bootstrapMethodIndex = bootstrapMethodIndex;
            this.nameAndTypeIndex = nameAndTypeIndex;
        }
    }

    private static final class LiteralEntry implements Entry {
        private final int tag;
        private LiteralEntry(int tag) { this.tag = tag; }
    }

    private enum ReservedEntry implements Entry { INSTANCE }
}
