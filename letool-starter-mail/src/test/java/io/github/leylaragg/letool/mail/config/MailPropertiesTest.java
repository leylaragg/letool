package io.github.leylaragg.letool.mail.config;

import io.github.leylaragg.letool.mail.exception.MailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MailProperties} 配置解析和校验测试。
 */
@DisplayName("MailProperties 配置校验测试")
class MailPropertiesTest {

    @Test
    @DisplayName("异步队列容量默认值应为有限正数")
    void shouldUseBoundedAsyncQueueByDefault() {
        MailProperties properties = new MailProperties();

        assertThat(properties.getAsyncQueueCapacity()).isEqualTo(1000);
    }

    @Test
    @DisplayName("默认账户不存在时应快速失败")
    void shouldRejectMissingDefaultAccount() {
        MailProperties properties = new MailProperties();

        assertThatThrownBy(properties::getActiveAccount)
                .isInstanceOfSatisfying(MailException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("MAIL_001");
                    assertThat(exception.getMessage()).contains("accounts.primary");
                });
    }

    @Test
    @DisplayName("请求指定账户时应解析对应配置")
    void shouldResolveRequestedAccount() {
        MailProperties properties = propertiesWithAccount("primary", validAccount());
        MailProperties.AccountConfig secondary = validAccount();
        secondary.setHost("smtp.secondary.example");
        properties.getAccounts().put("secondary", secondary);

        assertThat(properties.resolveAccount("secondary")).isSameAs(secondary);
        assertThat(properties.resolveAccountName("secondary")).isEqualTo("secondary");
        assertThat(properties.resolveAccount(null)).isSameAs(properties.getAccounts().get("primary"));
    }

    @Test
    @DisplayName("账户端口和超时参数必须为正数")
    void shouldRejectInvalidPortAndTimeouts() {
        MailProperties.AccountConfig invalidPort = validAccount();
        invalidPort.setPort(0);
        assertInvalidConfiguration(invalidPort, "accounts.primary.port");

        MailProperties.AccountConfig invalidConnectTimeout = validAccount();
        invalidConnectTimeout.setConnectionTimeoutMillis(0);
        assertInvalidConfiguration(
                invalidConnectTimeout,
                "accounts.primary.connection-timeout-millis"
        );

        MailProperties.AccountConfig invalidReadTimeout = validAccount();
        invalidReadTimeout.setReadTimeoutMillis(0);
        assertInvalidConfiguration(
                invalidReadTimeout,
                "accounts.primary.read-timeout-millis"
        );

        MailProperties.AccountConfig invalidWriteTimeout = validAccount();
        invalidWriteTimeout.setWriteTimeoutMillis(0);
        assertInvalidConfiguration(
                invalidWriteTimeout,
                "accounts.primary.write-timeout-millis"
        );
    }

    @Test
    @DisplayName("账户协议只能使用 smtp 或 smtps")
    void shouldRejectUnsupportedProtocol() {
        MailProperties.AccountConfig account = validAccount();
        account.setProtocol("imap");

        assertInvalidConfiguration(account, "accounts.primary.protocol");
    }

    @Test
    @DisplayName("默认发件人必须是合法邮箱地址")
    void shouldRejectInvalidDefaultSenderAddress() {
        MailProperties.AccountConfig account = validAccount();
        account.setFrom("not-an-email");

        assertInvalidConfiguration(account, "accounts.primary.from");
    }

    @Test
    @DisplayName("隐式 SSL 和 STARTTLS 不能同时启用")
    void shouldRejectConflictingTlsModes() {
        MailProperties.AccountConfig account = validAccount();
        account.setSsl(true);
        account.setStarttls(true);

        assertInvalidConfiguration(account, "accounts.primary.tls");
    }

    @Test
    @DisplayName("启用认证时必须配置用户名和密码")
    void shouldRequireCredentialsWhenAuthenticationIsEnabled() {
        MailProperties.AccountConfig missingUsername = validAccount();
        missingUsername.setUsername(" ");
        assertInvalidConfiguration(missingUsername, "accounts.primary.username");

        MailProperties.AccountConfig missingPassword = validAccount();
        missingPassword.setPassword(null);
        assertInvalidConfiguration(missingPassword, "accounts.primary.password");
    }

    @Test
    @DisplayName("关闭认证时允许省略用户名和密码")
    void shouldAllowMissingCredentialsWhenAuthenticationIsDisabled() {
        MailProperties.AccountConfig account = validAccount();
        account.setAuth(false);
        account.setUsername(null);
        account.setPassword(null);
        MailProperties properties = propertiesWithAccount("primary", account);

        assertThat(properties.getActiveAccount()).isSameAs(account);
    }

    /**
     * 创建包含一个账户的邮件配置。
     *
     * @param accountName 账户名称
     * @param account 账户配置
     * @return 邮件配置
     */
    private static MailProperties propertiesWithAccount(
            String accountName,
            MailProperties.AccountConfig account) {
        MailProperties properties = new MailProperties();
        properties.setDefaultAccount(accountName);
        properties.setAccounts(new java.util.HashMap<>(Map.of(accountName, account)));
        return properties;
    }

    /**
     * 创建可以通过校验的 SMTP 账户。
     *
     * @return 有效账户配置
     */
    private static MailProperties.AccountConfig validAccount() {
        MailProperties.AccountConfig account = new MailProperties.AccountConfig();
        account.setHost("smtp.example.com");
        account.setPort(587);
        account.setUsername("mailer@example.com");
        account.setPassword("secret");
        account.setProtocol("smtp");
        account.setAuth(true);
        account.setStarttls(true);
        account.setFrom("mailer@example.com");
        return account;
    }

    /**
     * 断言指定账户配置会产生稳定配置错误。
     *
     * @param account 待校验账户
     * @param field 预期安全字段名
     */
    private static void assertInvalidConfiguration(
            MailProperties.AccountConfig account,
            String field) {
        MailProperties properties = propertiesWithAccount("primary", account);

        assertThatThrownBy(properties::getActiveAccount)
                .isInstanceOfSatisfying(MailException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("MAIL_001");
                    assertThat(exception.getMessage()).contains(field);
                });
    }
}
