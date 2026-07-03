package com.github.leyland.letool.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

// ======================== 类级别说明 ========================

/**
 * <p>邮件模块的全局配置属性类，绑定 {@code letool.mail} 前缀的配置项。</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>管理邮件模块的全局开关 ({@code enabled}) 与异步发送策略。</li>
 *   <li>管理多账户配置 ({@code accounts})，支持为不同业务场景配置独立的 SMTP 账户。</li>
 *   <li>通过 {@link #getActiveAccount()} 获取当前生效的账户配置。</li>
 * </ul>
 *
 * <h3>配置示例 (application.yml)</h3>
 * <pre>{@code
 * letool:
 *   mail:
 *     enabled: true
 *     default-account: primary
 *     async: true
 *     async-pool-size: 4
 *     accounts:
 *       primary:
 *         host: smtp.example.com
 *         port: 587
 *         username: user@example.com
 *         password: yourpassword
 *         protocol: smtp
 *         auth: true
 *         starttls: true
 *         ssl: false
 *         from: noreply@example.com
 *         personal: "系统通知"
 * }</pre>
 *
 * <h3>设计说明</h3>
 * <ul>
 *   <li>配置项不存在时，{@code defaultAccount} 默认为 {@code "primary"}，{@code accounts} 为空 Map，
 *       此时 {@link #getActiveAccount()} 将返回一个全默认值的 {@link AccountConfig}。</li>
 *   <li>{@link AccountConfig} 为静态内部类，与外部配置结构一一对应，便于 Spring Boot 的类型安全绑定。</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "letool.mail")
public class MailProperties {

    // ======================== 全局配置字段 ========================

    /** 邮件模块总开关，默认关闭 */
    private boolean enabled = false;

    /** 默认使用的账户名称，对应 {@code accounts} 中的 key */
    private String defaultAccount = "primary";

    /** 多账户配置集合，key 为账户名称 */
    private Map<String, AccountConfig> accounts = new HashMap<>();

    /** 是否启用异步发送，默认同步 */
    private boolean async = false;

    /** 异步发送线程池大小，默认 4 */
    private int asyncPoolSize = 4;

    // ======================== Getter / Setter ========================

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDefaultAccount() { return defaultAccount; }
    public void setDefaultAccount(String defaultAccount) { this.defaultAccount = defaultAccount; }
    public Map<String, AccountConfig> getAccounts() { return accounts; }
    public void setAccounts(Map<String, AccountConfig> accounts) { this.accounts = accounts; }
    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }
    public int getAsyncPoolSize() { return asyncPoolSize; }
    public void setAsyncPoolSize(int asyncPoolSize) { this.asyncPoolSize = asyncPoolSize; }

    // ======================== 公共方法 ========================

    /**
     * 获取当前生效的账户配置。
     *
     * <p>根据 {@code defaultAccount} 从 {@code accounts} 中查找对应配置；
     * 若未找到，返回一个全默认值的 {@link AccountConfig} 实例。</p>
     *
     * @return 当前生效的账户配置，不会返回 {@code null}
     */
    public AccountConfig getActiveAccount() {
        return accounts.getOrDefault(defaultAccount, new AccountConfig());
    }

    // ======================== 内部类：单账户配置 ========================

    /**
     * <p>单个 SMTP 账户的配置项，与 {@code letool.mail.accounts.<name>} 下的属性一一对应。</p>
     *
     * <h3>字段说明</h3>
     * <ul>
     *   <li><b>host</b> - SMTP 服务器地址，默认 {@code localhost}</li>
     *   <li><b>port</b> - SMTP 服务器端口，默认 25</li>
     *   <li><b>username</b> - 登录用户名</li>
     *   <li><b>password</b> - 登录密码</li>
     *   <li><b>protocol</b> - 传输协议 ({@code smtp} / {@code smtps})，默认 {@code smtp}</li>
     *   <li><b>auth</b> - 是否启用认证，默认 {@code true}</li>
     *   <li><b>starttls</b> - 是否启用 STARTTLS，默认 {@code false}</li>
     *   <li><b>ssl</b> - 是否启用 SSL，默认 {@code false}</li>
     *   <li><b>from</b> - 发件人地址</li>
     *   <li><b>personal</b> - 发件人显示名称</li>
     * </ul>
     *
     * @author leyland
     * @since 1.0.0
     */
    public static class AccountConfig {

        // ======================== SMTP 配置字段 ========================

        private String host = "localhost";
        private int port = 25;
        private String username;
        private String password;
        private String protocol = "smtp";
        private boolean auth = true;
        private boolean starttls = false;
        private boolean ssl = false;
        private String from;
        private String personal;

        // ======================== Getter / Setter ========================

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public boolean isAuth() { return auth; }
        public void setAuth(boolean auth) { this.auth = auth; }
        public boolean isStarttls() { return starttls; }
        public void setStarttls(boolean starttls) { this.starttls = starttls; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getPersonal() { return personal; }
        public void setPersonal(String personal) { this.personal = personal; }
    }
}
