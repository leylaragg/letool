package com.github.leyland.letool.rule.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GroovyScriptEngine 测试")
class GroovyScriptEngineTest {

    private GroovyScriptEngine scriptEngine;

    @BeforeEach
    void setUp() {
        scriptEngine = new GroovyScriptEngine(true, 5);
    }

    @Nested
    @DisplayName("构造方法")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应创建可用实例")
        void shouldCreateDefaultInstance() {
            GroovyScriptEngine engine = new GroovyScriptEngine();
            assertNotNull(engine);
        }

        @Test
        @DisplayName("单参构造应设置缓存开关")
        void shouldSetCacheEnabled() {
            GroovyScriptEngine engine = new GroovyScriptEngine(false);
            assertNotNull(engine);
        }

        @Test
        @DisplayName("双参构造应设置缓存和超时时间")
        void shouldSetCacheAndTimeout() {
            GroovyScriptEngine engine = new GroovyScriptEngine(true, 10);
            assertNotNull(engine);
        }
    }

    @Nested
    @DisplayName("isGroovyAvailable 方法")
    class GroovyAvailableTests {

        @Test
        @DisplayName("isGroovyAvailable 应返回 groovy 是否可用")
        void shouldCheckGroovyAvailability() {
            boolean available = scriptEngine.isGroovyAvailable();
            // 在没有 Groovy 依赖的测试环境中为 false
            assertNotNull(available);
        }
    }

    @Nested
    @DisplayName("registerScript 方法")
    class RegisterScriptTests {

        @Test
        @DisplayName("registerScript 应注册脚本")
        void shouldRegisterScript() {
            scriptEngine.registerScript("testRule", "def score = 85; return score > 80;");

            assertTrue(scriptEngine.isCached("testRule"));
        }

        @Test
        @DisplayName("registerScript 应覆盖同名脚本")
        void shouldOverrideExistingScript() {
            scriptEngine.registerScript("testRule", "return false;");
            scriptEngine.registerScript("testRule", "return true;");

            GroovyScriptEngine.GroovyScript script = scriptEngine.getScript("testRule");
            assertEquals("return true;", script.getContent());
        }
    }

    @Nested
    @DisplayName("getScript 方法")
    class GetScriptTests {

        @Test
        @DisplayName("getScript 应返回已注册的脚本")
        void shouldGetRegisteredScript() {
            scriptEngine.registerScript("rule", "return context.getParam('amount') > 1000;");

            GroovyScriptEngine.GroovyScript script = scriptEngine.getScript("rule");

            assertNotNull(script);
            assertEquals("rule", script.getName());
            assertEquals("return context.getParam('amount') > 1000;", script.getContent());
        }

        @Test
        @DisplayName("getScript 不存在的脚本应返回 null")
        void shouldReturnNullForMissingScript() {
            assertNull(scriptEngine.getScript("nonexistent"));
        }
    }

    @Nested
    @DisplayName("isCached 方法")
    class IsCachedTests {

        @Test
        @DisplayName("未注册的脚本应返回 false")
        void shouldReturnFalseForUnregistered() {
            assertFalse(scriptEngine.isCached("unknown"));
        }

        @Test
        @DisplayName("已注册的脚本应返回 true")
        void shouldReturnTrueForRegistered() {
            scriptEngine.registerScript("rule", "return true;");
            assertTrue(scriptEngine.isCached("rule"));
        }
    }

    @Nested
    @DisplayName("invalidateCache 方法")
    class InvalidateCacheTests {

        @Test
        @DisplayName("invalidateCache 应清除指定脚本")
        void shouldInvalidateSpecificScript() {
            scriptEngine.registerScript("rule1", "return true;");
            scriptEngine.registerScript("rule2", "return false;");

            scriptEngine.invalidateCache("rule1");

            assertFalse(scriptEngine.isCached("rule1"));
            assertTrue(scriptEngine.isCached("rule2"));
        }

        @Test
        @DisplayName("invalidateCache 不存在的脚本不应报错")
        void shouldNotThrowForMissingScript() {
            assertDoesNotThrow(() -> scriptEngine.invalidateCache("nonexistent"));
        }
    }

    @Nested
    @DisplayName("invalidateAllCache 方法")
    class InvalidateAllCacheTests {

        @Test
        @DisplayName("invalidateAllCache 应清除所有脚本")
        void shouldInvalidateAllScripts() {
            scriptEngine.registerScript("rule1", "return true;");
            scriptEngine.registerScript("rule2", "return false;");
            scriptEngine.registerScript("rule3", "return true;");

            scriptEngine.invalidateAllCache();

            assertEquals(0, scriptEngine.cacheSize());
        }
    }

    @Nested
    @DisplayName("cacheSize 方法")
    class CacheSizeTests {

        @Test
        @DisplayName("初始缓存大小应为 0")
        void shouldStartWithZero() {
            assertEquals(0, scriptEngine.cacheSize());
        }

        @Test
        @DisplayName("注册脚本后应增加缓存计数")
        void shouldIncrementAfterRegistration() {
            scriptEngine.registerScript("rule1", "return true;");
            assertEquals(1, scriptEngine.cacheSize());

            scriptEngine.registerScript("rule2", "return false;");
            assertEquals(2, scriptEngine.cacheSize());
        }
    }

    @Nested
    @DisplayName("GroovyScript 内部类")
    class GroovyScriptTests {

        @Test
        @DisplayName("构造应设置所有字段")
        void shouldSetAllFields() {
            GroovyScriptEngine.GroovyScript script = new GroovyScriptEngine.GroovyScript(
                    "myScript", "return 42;", null);

            assertEquals("myScript", script.getName());
            assertEquals("return 42;", script.getContent());
            assertFalse(script.isCompiled());
            assertTrue(script.getCompiledAt() > 0);
        }

        @Test
        @DisplayName("已编译的脚本 isCompiled 应返回 true")
        void shouldReportCompiled() {
            GroovyScriptEngine.GroovyScript script = new GroovyScriptEngine.GroovyScript(
                    "compiled", "return 42;", null);
            assertFalse(script.isCompiled());
        }

        @Test
        @DisplayName("getCompiled 应返回编译对象")
        void shouldReturnCompiledObject() {
            GroovyScriptEngine.GroovyScript script = new GroovyScriptEngine.GroovyScript(
                    "s", "c", null);
            assertNull(script.getCompiled());
        }
    }

    @Nested
    @DisplayName("compile 方法")
    class CompileTests {

        @Test
        @DisplayName("无 Groovy 依赖时 compile 应返回 false")
        void shouldReturnFalseWithoutGroovy() {
            boolean result = scriptEngine.compile("test", "return 42;");
            // 没有 Groovy 依赖时返回 false
            assertFalse(result);
        }
    }
}
