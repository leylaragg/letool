package io.github.leylaragg.letool.mail.config;

import io.github.leylaragg.letool.mail.exception.MailException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 绑定 {@code letool.mail} 前缀的邮件模块配置。
 *
 * <p>模块支持配置多个 SMTP 账户。调用方未指定账户时使用
 * {@link #defaultAccount}；指定账户时通过 {@link #resolveAccount(String)}
 * 获取并校验对应配置。账户不存在或关键传输参数不合法时会立即失败，
 * 不会静默回退到本地 SMTP。</p>
 */
@ConfigurationProperties(prefix = "letool.mail")
public class MailProperties {

    /** 邮件模块总开关，默认关闭。 */
    private boolean enabled;

    /** 未在请求中指定账户时使用的默认账户名称。 */
    private String defaultAccount = "primary";

    /** 按名称保存的 SMTP 账户配置。 */
    private Map<String, AccountConfig> accounts = new HashMap<>();

    /** 异步发送线程池大小。 */
    private int asyncPoolSize = 4;

    /** 等待执行的最大异步邮件任务数。 */
    private int asyncQueueCapacity = 1000;

    /**
     * 判断邮件模块是否启用。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置邮件模块开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认账户名称。
     *
     * @return 默认账户名称
     */
    public String getDefaultAccount() {
        return defaultAccount;
    }

    /**
     * 设置默认账户名称。
     *
     * @param defaultAccount 默认账户名称
     */
    public void setDefaultAccount(String defaultAccount) {
        this.defaultAccount = defaultAccount;
    }

    /**
     * 获取可由 Spring Boot 绑定的账户映射。
     *
     * @return 可变账户映射
     */
    public Map<String, AccountConfig> getAccounts() {
        return accounts;
    }

    /**
     * 设置账户映射。
     *
     * @param accounts 账户映射；为 {@code null} 时按空映射处理
     */
    public void setAccounts(Map<String, AccountConfig> accounts) {
        this.accounts = accounts == null ? new HashMap<>() : new HashMap<>(accounts);
    }

    /**
     * 获取异步线程池大小。
     *
     * @return 线程池大小
     */
    public int getAsyncPoolSize() {
        return asyncPoolSize;
    }

    /**
     * 设置异步线程池大小。
     *
     * @param asyncPoolSize 线程池大小
     */
    public void setAsyncPoolSize(int asyncPoolSize) {
        this.asyncPoolSize = asyncPoolSize;
    }

    /**
     * 获取异步任务队列容量。
     *
     * @return 最大等待任务数
     */
    public int getAsyncQueueCapacity() {
        return asyncQueueCapacity;
    }

    /**
     * 设置异步任务队列容量。
     *
     * @param asyncQueueCapacity 最大等待任务数
     */
    public void setAsyncQueueCapacity(int asyncQueueCapacity) {
        this.asyncQueueCapacity = asyncQueueCapacity;
    }

    /**
     * 获取并校验默认账户配置。
     *
     * @return 默认账户配置
     * @throws MailException 当默认账户不存在或配置不合法时抛出
     */
    public AccountConfig getActiveAccount() {
        return resolveAccount(null);
    }

    /**
     * 解析请求最终使用的账户名称。
     *
     * @param requestedAccount 请求指定的账户名称；为 {@code null} 时使用默认账户
     * @return 已解析的非空白账户名称
     * @throws MailException 当账户名称为空白时抛出
     */
    public String resolveAccountName(String requestedAccount) {
        String accountName = requestedAccount == null
                ? defaultAccount
                : requestedAccount;
        if (accountName == null || accountName.isBlank()) {
            throw MailException.configurationInvalid(
                    requestedAccount == null ? "default-account" : "account-name"
            );
        }
        return accountName;
    }

    /**
     * 解析并校验请求使用的 SMTP 账户。
     *
     * @param requestedAccount 请求指定的账户名称；为 {@code null} 时使用默认账户
     * @return 已校验账户配置
     * @throws MailException 当账户不存在或配置不合法时抛出
     */
    public AccountConfig resolveAccount(String requestedAccount) {
        String accountName = resolveAccountName(requestedAccount);
        AccountConfig account = accounts.get(accountName);
        if (account == null) {
            throw MailException.configurationInvalid("accounts." + accountName);
        }
        validateAccount(accountName, account);
        return account;
    }

    /**
     * 校验单个 SMTP 账户的生产必需参数。
     *
     * @param accountName 账户名称
     * @param account 账户配置
     */
    private static void validateAccount(String accountName, AccountConfig account) {
        String prefix = "accounts." + accountName + ".";
        requireText(account.getHost(), prefix + "host");
        if (account.getPort() < 1 || account.getPort() > 65535) {
            throw MailException.configurationInvalid(prefix + "port");
        }
        String protocol = requireText(account.getProtocol(), prefix + "protocol")
                .toLowerCase(Locale.ROOT);
        if (!protocol.equals("smtp") && !protocol.equals("smtps")) {
            throw MailException.configurationInvalid(prefix + "protocol");
        }
        validateAddress(account.getFrom(), prefix + "from");
        if (account.isAuth()) {
            requireText(account.getUsername(), prefix + "username");
            requireText(account.getPassword(), prefix + "password");
        }
        if (account.isSsl() && account.isStarttls()) {
            throw MailException.configurationInvalid(prefix + "tls");
        }
        requirePositive(
                account.getConnectionTimeoutMillis(),
                prefix + "connection-timeout-millis"
        );
        requirePositive(account.getReadTimeoutMillis(), prefix + "read-timeout-millis");
        requirePositive(account.getWriteTimeoutMillis(), prefix + "write-timeout-millis");
    }

    /**
     * 校验必填文本。
     *
     * @param value 待校验文本
     * @param field 安全配置字段名
     * @return 原始非空白文本
     */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw MailException.configurationInvalid(field);
        }
        return value;
    }

    /**
     * 校验账户默认发件人地址。
     *
     * @param address 待校验地址
     * @param field 安全配置字段名
     */
    private static void validateAddress(String address, String field) {
        requireText(address, field);
        try {
            InternetAddress[] parsed = InternetAddress.parse(address, true);
            String mailbox = parsed.length == 1 ? parsed[0].getAddress() : null;
            if (mailbox == null
                    || mailbox.indexOf('@') <= 0
                    || mailbox.endsWith("@")) {
                throw MailException.configurationInvalid(field);
            }
            parsed[0].validate();
        } catch (AddressException exception) {
            throw MailException.configurationInvalid(field);
        }
    }

    /**
     * 校验正整数配置。
     *
     * @param value 待校验数值
     * @param field 安全配置字段名
     */
    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw MailException.configurationInvalid(field);
        }
    }

    /**
     * 单个 SMTP 账户的连接和发件人配置。
     */
    public static class AccountConfig {

        /** SMTP 服务器地址。 */
        private String host = "localhost";

        /** SMTP 服务器端口。 */
        private int port = 25;

        /** 启用认证时使用的用户名。 */
        private String username;

        /** 启用认证时使用的密码。 */
        private String password;

        /** 传输协议，仅支持 smtp 或 smtps。 */
        private String protocol = "smtp";

        /** 是否启用 SMTP 认证。 */
        private boolean auth = true;

        /** 是否启用 STARTTLS。 */
        private boolean starttls;

        /** 是否启用隐式 SSL。 */
        private boolean ssl;

        /** 默认发件人地址。 */
        private String from;

        /** 默认发件人显示名称。 */
        private String personal;

        /** 建立连接的超时时间，单位毫秒。 */
        private int connectionTimeoutMillis = 10_000;

        /** 等待读取响应的超时时间，单位毫秒。 */
        private int readTimeoutMillis = 10_000;

        /** 写入 SMTP 数据的超时时间，单位毫秒。 */
        private int writeTimeoutMillis = 10_000;

        /**
         * 获取 SMTP 服务器地址。
         *
         * @return 服务器地址
         */
        public String getHost() {
            return host;
        }

        /**
         * 设置 SMTP 服务器地址。
         *
         * @param host 服务器地址
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 获取 SMTP 服务器端口。
         *
         * @return 服务器端口
         */
        public int getPort() {
            return port;
        }

        /**
         * 设置 SMTP 服务器端口。
         *
         * @param port 服务器端口
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 获取认证用户名。
         *
         * @return 认证用户名
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置认证用户名。
         *
         * @param username 认证用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 获取认证密码。
         *
         * @return 认证密码
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置认证密码。
         *
         * @param password 认证密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 获取传输协议。
         *
         * @return smtp 或 smtps
         */
        public String getProtocol() {
            return protocol;
        }

        /**
         * 设置传输协议。
         *
         * @param protocol smtp 或 smtps
         */
        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        /**
         * 判断是否启用认证。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isAuth() {
            return auth;
        }

        /**
         * 设置是否启用认证。
         *
         * @param auth 是否启用
         */
        public void setAuth(boolean auth) {
            this.auth = auth;
        }

        /**
         * 判断是否启用 STARTTLS。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isStarttls() {
            return starttls;
        }

        /**
         * 设置是否启用 STARTTLS。
         *
         * @param starttls 是否启用
         */
        public void setStarttls(boolean starttls) {
            this.starttls = starttls;
        }

        /**
         * 判断是否启用隐式 SSL。
         *
         * @return 启用时返回 {@code true}
         */
        public boolean isSsl() {
            return ssl;
        }

        /**
         * 设置是否启用隐式 SSL。
         *
         * @param ssl 是否启用
         */
        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        /**
         * 获取默认发件人地址。
         *
         * @return 发件人地址
         */
        public String getFrom() {
            return from;
        }

        /**
         * 设置默认发件人地址。
         *
         * @param from 发件人地址
         */
        public void setFrom(String from) {
            this.from = from;
        }

        /**
         * 获取默认发件人显示名称。
         *
         * @return 显示名称
         */
        public String getPersonal() {
            return personal;
        }

        /**
         * 设置默认发件人显示名称。
         *
         * @param personal 显示名称
         */
        public void setPersonal(String personal) {
            this.personal = personal;
        }

        /**
         * 获取连接超时。
         *
         * @return 毫秒数
         */
        public int getConnectionTimeoutMillis() {
            return connectionTimeoutMillis;
        }

        /**
         * 设置连接超时。
         *
         * @param connectionTimeoutMillis 毫秒数
         */
        public void setConnectionTimeoutMillis(int connectionTimeoutMillis) {
            this.connectionTimeoutMillis = connectionTimeoutMillis;
        }

        /**
         * 获取读取超时。
         *
         * @return 毫秒数
         */
        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        /**
         * 设置读取超时。
         *
         * @param readTimeoutMillis 毫秒数
         */
        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }

        /**
         * 获取写入超时。
         *
         * @return 毫秒数
         */
        public int getWriteTimeoutMillis() {
            return writeTimeoutMillis;
        }

        /**
         * 设置写入超时。
         *
         * @param writeTimeoutMillis 毫秒数
         */
        public void setWriteTimeoutMillis(int writeTimeoutMillis) {
            this.writeTimeoutMillis = writeTimeoutMillis;
        }
    }
}
