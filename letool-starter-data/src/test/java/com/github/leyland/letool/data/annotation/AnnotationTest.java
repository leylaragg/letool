package com.github.leyland.letool.data.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据模块注解的单元测试 —— 验证 {@code @Table}, {@code @Column}, {@code @Id}, {@code @Transient} 的元信息与运行时行为。
 */
@DisplayName("数据注解测试")
class AnnotationTest {

    // ======================== 辅助模型类 ========================

    @Table("t_user")
    static class UserEntity {
        @Id
        @Column("user_id")
        private Long id;

        @Column("user_name")
        private String name;

        @Transient
        private String tempData;

        // 无注解字段
        private Integer age;
    }

    static class NoTableEntity {
        @Id
        private String code;
    }

    // ======================== @Table 注解测试 ========================

    @Nested
    @DisplayName("@Table 注解测试")
    class TableAnnotationTests {

        @Test
        @DisplayName("应存在于类级别")
        void shouldBePresentAtClassLevel() {
            Table table = UserEntity.class.getAnnotation(Table.class);
            assertNotNull(table, "@Table 注解应存在于类上");
        }

        @Test
        @DisplayName("应返回正确的表名值")
        void shouldReturnCorrectTableName() {
            Table table = UserEntity.class.getAnnotation(Table.class);
            assertEquals("t_user", table.value(), "表名应为 t_user");
        }

        @Test
        @DisplayName("未标注 @Table 的类应无此注解")
        void shouldNotBePresentOnUnannotatedClass() {
            Table table = NoTableEntity.class.getAnnotation(Table.class);
            assertNull(table, "未标注的类不应有 @Table 注解");
        }

        @Test
        @DisplayName("Target 应为 TYPE")
        void targetShouldBeType() throws NoSuchMethodException {
            java.lang.annotation.Target target = Table.class.getAnnotation(java.lang.annotation.Target.class);
            assertNotNull(target);
            boolean hasType = false;
            for (java.lang.annotation.ElementType et : target.value()) {
                if (et == java.lang.annotation.ElementType.TYPE) hasType = true;
            }
            assertTrue(hasType, "@Table 的 @Target 应包含 TYPE");
        }

        @Test
        @DisplayName("Retention 应为 RUNTIME")
        void retentionShouldBeRuntime() {
            java.lang.annotation.Retention retention = Table.class.getAnnotation(java.lang.annotation.Retention.class);
            assertNotNull(retention);
            assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value(),
                    "@Table 的 @Retention 应为 RUNTIME");
        }

        @Test
        @DisplayName("应有 @Documented 元注解")
        void shouldHaveDocumentedMetaAnnotation() {
            Documented doc = Table.class.getAnnotation(Documented.class);
            assertNotNull(doc, "@Table 应有 @Documented 注解");
        }
    }

    // ======================== @Column 注解测试 ========================

    @Nested
    @DisplayName("@Column 注解测试")
    class ColumnAnnotationTests {

        @Test
        @DisplayName("应存在于字段级别")
        void shouldBePresentOnField() throws NoSuchFieldException {
            Field field = UserEntity.class.getDeclaredField("name");
            Column column = field.getAnnotation(Column.class);
            assertNotNull(column, "字段 name 上应有 @Column 注解");
        }

        @Test
        @DisplayName("应返回正确的列名值")
        void shouldReturnCorrectColumnName() throws NoSuchFieldException {
            Field field = UserEntity.class.getDeclaredField("id");
            Column column = field.getAnnotation(Column.class);
            assertEquals("user_id", column.value(), "列名应为 user_id");
        }

        @Test
        @DisplayName("未标注 @Column 的字段应无此注解")
        void shouldNotBePresentOnUnannotatedField() throws NoSuchFieldException {
            Field field = UserEntity.class.getDeclaredField("age");
            Column column = field.getAnnotation(Column.class);
            assertNull(column, "未标注的字段不应有 @Column 注解");
        }

        @Test
        @DisplayName("Target 应为 FIELD")
        void targetShouldBeField() {
            java.lang.annotation.Target target = Column.class.getAnnotation(java.lang.annotation.Target.class);
            assertNotNull(target);
            boolean hasField = false;
            for (java.lang.annotation.ElementType et : target.value()) {
                if (et == java.lang.annotation.ElementType.FIELD) hasField = true;
            }
            assertTrue(hasField, "@Column 的 @Target 应包含 FIELD");
        }

        @Test
        @DisplayName("Retention 应为 RUNTIME")
        void retentionShouldBeRuntime() {
            java.lang.annotation.Retention retention = Column.class.getAnnotation(java.lang.annotation.Retention.class);
            assertNotNull(retention);
            assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value(),
                    "@Column 的 @Retention 应为 RUNTIME");
        }
    }

    // ======================== @Id 注解测试 ========================

    @Nested
    @DisplayName("@Id 注解测试")
    class IdAnnotationTests {

        @Test
        @DisplayName("应存在于主键字段上")
        void shouldBePresentOnIdField() throws NoSuchFieldException {
            Field field = UserEntity.class.getDeclaredField("id");
            Id id = field.getAnnotation(Id.class);
            assertNotNull(id, "字段 id 上应有 @Id 注解");
        }

        @Test
        @DisplayName("无 Table 注解放到带 @Id 字段上应能识别")
        void shouldBeRecognizedOnClassWithoutTable() throws NoSuchFieldException {
            Field field = NoTableEntity.class.getDeclaredField("code");
            Id id = field.getAnnotation(Id.class);
            assertNotNull(id, "即使在无 @Table 注解的类中，@Id 注解也应正常工作");
        }

        @Test
        @DisplayName("Target 应为 FIELD")
        void targetShouldBeField() {
            java.lang.annotation.Target target = Id.class.getAnnotation(java.lang.annotation.Target.class);
            assertNotNull(target);
            boolean hasField = false;
            for (java.lang.annotation.ElementType et : target.value()) {
                if (et == java.lang.annotation.ElementType.FIELD) hasField = true;
            }
            assertTrue(hasField, "@Id 的 @Target 应包含 FIELD");
        }

        @Test
        @DisplayName("Retention 应为 RUNTIME")
        void retentionShouldBeRuntime() {
            java.lang.annotation.Retention retention = Id.class.getAnnotation(java.lang.annotation.Retention.class);
            assertNotNull(retention);
            assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value(),
                    "@Id 的 @Retention 应为 RUNTIME");
        }
    }

    // ======================== @Transient 注解测试 ========================

    @Nested
    @DisplayName("@Transient 注解测试")
    class TransientAnnotationTests {

        @Test
        @DisplayName("应存在于临时字段上")
        void shouldBePresentOnTransientField() throws NoSuchFieldException {
            Field field = UserEntity.class.getDeclaredField("tempData");
            Transient transientAnn = field.getAnnotation(Transient.class);
            assertNotNull(transientAnn, "字段 tempData 上应有 @Transient 注解");
        }

        @Test
        @DisplayName("标注 @Transient 的字段仍可通过反射读写")
        void transientFieldShouldStillBeAccessible() throws Exception {
            UserEntity entity = new UserEntity();
            Field field = UserEntity.class.getDeclaredField("tempData");
            field.setAccessible(true);
            field.set(entity, "test");
            assertEquals("test", field.get(entity), "@Transient 仅作为标记，不应阻止字段读写");
        }

        @Test
        @DisplayName("Target 应为 FIELD")
        void targetShouldBeField() {
            java.lang.annotation.Target target = Transient.class.getAnnotation(java.lang.annotation.Target.class);
            assertNotNull(target);
            boolean hasField = false;
            for (java.lang.annotation.ElementType et : target.value()) {
                if (et == java.lang.annotation.ElementType.FIELD) hasField = true;
            }
            assertTrue(hasField, "@Transient 的 @Target 应包含 FIELD");
        }

        @Test
        @DisplayName("Retention 应为 RUNTIME")
        void retentionShouldBeRuntime() {
            java.lang.annotation.Retention retention = Transient.class.getAnnotation(java.lang.annotation.Retention.class);
            assertNotNull(retention);
            assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value(),
                    "@Transient 的 @Retention 应为 RUNTIME");
        }
    }

    // ======================== 组合使用测试 ========================

    @Nested
    @DisplayName("组合使用测试")
    class CombinedUsageTests {

        @Test
        @DisplayName("@Id 与 @Column 可共存于同一字段")
        void idAndColumnCanCoexistOnSameField() throws NoSuchFieldException {
            Field field = UserEntity.class.getDeclaredField("id");
            Id id = field.getAnnotation(Id.class);
            Column column = field.getAnnotation(Column.class);
            assertNotNull(id);
            assertNotNull(column);
            assertEquals("user_id", column.value());
        }

        @Test
        @DisplayName("模型类应有 4 个声明字段，其中 1 个标注 @Transient")
        void modelClassShouldHaveCorrectFieldCount() {
            Field[] fields = UserEntity.class.getDeclaredFields();
            assertEquals(4, fields.length, "应有 4 个声明字段");

            int transientCount = 0;
            for (Field f : fields) {
                if (f.isAnnotationPresent(Transient.class)) transientCount++;
            }
            assertEquals(1, transientCount, "应有 1 个 @Transient 字段");
        }
    }
}
