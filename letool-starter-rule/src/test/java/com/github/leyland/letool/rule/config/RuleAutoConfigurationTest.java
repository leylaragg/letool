package com.github.leyland.letool.rule.config;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import com.github.leyland.letool.rule.chain.ChainManager;
import com.github.leyland.letool.rule.chain.ChainParser;
import com.github.leyland.letool.rule.engine.GroovyScriptEngine;
import com.github.leyland.letool.rule.engine.RuleEngine;
import com.github.leyland.letool.rule.hotreload.FileWatcher;
import com.github.leyland.letool.rule.hotreload.RuleHotReloadListener;
import com.github.leyland.letool.rule.monitor.RuleMonitor;
import com.github.leyland.letool.rule.store.RuleStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration contract tests for {@link RuleAutoConfiguration}.
 */
class RuleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuleAutoConfiguration.class))
            .withPropertyValues(
                    "letool.rule.source=manual",
                    "letool.rule.hot-reload.enabled=false");

    @Test
    void shouldCreateDefaultRuleEngineBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RuleProperties.class);
            assertThat(context).hasSingleBean(ChainParser.class);
            assertThat(context).hasSingleBean(ChainManager.class);
            assertThat(context).hasSingleBean(GroovyScriptEngine.class);
            assertThat(context).hasSingleBean(RuleEngine.class);
            assertThat(context).hasSingleBean(RuleStore.class);
            assertThat(context).hasSingleBean(RuleMonitor.class);
            assertThat(context).doesNotHaveBean(FileWatcher.class);
            assertThat(context).doesNotHaveBean(RuleHotReloadListener.class);
            assertThat(context).doesNotHaveBean("ruleController");
        });
    }

    @Test
    void shouldDisableRuleAutoConfiguration() {
        contextRunner
                .withPropertyValues("letool.rule.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RuleProperties.class);
                    assertThat(context).doesNotHaveBean(ChainParser.class);
                    assertThat(context).doesNotHaveBean(RuleEngine.class);
                });
    }

    @Test
    void shouldDisableRuleMonitoringOnly() {
        contextRunner
                .withPropertyValues("letool.rule.monitoring.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(RuleEngine.class);
                    assertThat(context).doesNotHaveBean(RuleMonitor.class);
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesRuleBeans() {
        contextRunner
                .withUserConfiguration(UserRuleConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ChainParser.class);
                    assertThat(context).hasSingleBean(RuleStore.class);
                    assertThat(context).hasSingleBean(RuleMonitor.class);
                    assertThat(context.getBean(ChainParser.class))
                            .isSameAs(context.getBean("userChainParser"));
                    assertThat(context.getBean(RuleStore.class))
                            .isSameAs(context.getBean("userRuleStore"));
                    assertThat(context.getBean(RuleMonitor.class))
                            .isSameAs(context.getBean("userRuleMonitor"));
                });
    }

    @Test
    void shouldStartWithoutSpringWebOnClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.web"))
                .run(context -> {
                    assertThat(context).hasSingleBean(RuleEngine.class);
                    assertThat(context).doesNotHaveBean("ruleController");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserRuleConfiguration {

        @Bean
        ChainParser userChainParser() {
            return new ChainParser();
        }

        @Bean
        RuleStore userRuleStore() {
            return new StubRuleStore();
        }

        @Bean
        RuleMonitor userRuleMonitor() {
            return new RuleMonitor();
        }
    }

    static class StubRuleStore implements RuleStore {

        @Override
        public ChainDefinition load(String name) {
            return null;
        }

        @Override
        public void save(ChainDefinition chain) {
            // Stub implementation for auto-configuration back-off tests.
        }

        @Override
        public void delete(String name) {
            // Stub implementation for auto-configuration back-off tests.
        }

        @Override
        public List<ChainDefinition> listAll() {
            return Collections.emptyList();
        }
    }
}
