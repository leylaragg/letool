package com.github.leyland.letool.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件模块强类型配置，对应 {@code letool.file} 前缀。
 */
@ConfigurationProperties(prefix = "letool.file")
public class FileProperties {

    private boolean enabled = true;
    private final Upload upload = new Upload();
    private final Storage storage = new Storage();
    private final Archive archive = new Archive();

    /**
     * 判断文件模块是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置文件模块开关。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取上传配置。
     *
     * @return 上传配置
     */
    public Upload getUpload() {
        return upload;
    }

    /**
     * 获取存储配置。
     *
     * @return 存储配置
     */
    public Storage getStorage() {
        return storage;
    }

    /**
     * 获取归档配置。
     *
     * @return 归档配置
     */
    public Archive getArchive() {
        return archive;
    }

    /**
     * 普通上传的安全限制。
     */
    public static class Upload {
        private DataSize maxSize = DataSize.ofMegabytes(10);
        private boolean allowEmpty;
        private List<String> allowedExtensions = new ArrayList<>();
        private List<String> allowedContentTypes = new ArrayList<>();

        /**
         * 获取单文件最大大小。
         *
         * @return 最大文件大小
         */
        public DataSize getMaxSize() {
            return maxSize;
        }

        /**
         * 设置单文件最大大小。
         *
         * @param maxSize 最大文件大小
         */
        public void setMaxSize(DataSize maxSize) {
            this.maxSize = maxSize;
        }

        /**
         * 判断是否允许空文件。
         *
         * @return 是否允许空文件
         */
        public boolean isAllowEmpty() {
            return allowEmpty;
        }

        /**
         * 设置是否允许空文件。
         *
         * @param allowEmpty 是否允许空文件
         */
        public void setAllowEmpty(boolean allowEmpty) {
            this.allowEmpty = allowEmpty;
        }

        /**
         * 获取允许的扩展名列表。
         *
         * @return 扩展名列表
         */
        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        /**
         * 设置允许的扩展名列表。
         *
         * @param allowedExtensions 扩展名列表；空值表示不限制
         */
        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions == null
                    ? new ArrayList<>()
                    : new ArrayList<>(allowedExtensions);
        }

        /**
         * 获取允许的媒体类型列表。
         *
         * @return 媒体类型列表
         */
        public List<String> getAllowedContentTypes() {
            return allowedContentTypes;
        }

        /**
         * 设置允许的媒体类型列表。
         *
         * @param allowedContentTypes 媒体类型列表；空值表示不限制
         */
        public void setAllowedContentTypes(List<String> allowedContentTypes) {
            this.allowedContentTypes = allowedContentTypes == null
                    ? new ArrayList<>()
                    : new ArrayList<>(allowedContentTypes);
        }
    }

    /**
     * 存储类型和协议配置。
     */
    public static class Storage {
        private String type = "local";
        private final Local local = new Local();
        private final Ftp ftp = new Ftp();

        /**
         * 获取存储类型。
         *
         * @return {@code local}、{@code ftp} 或 {@code ftps}
         */
        public String getType() {
            return type;
        }

        /**
         * 设置存储类型。
         *
         * @param type 存储类型
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * 获取本地存储配置。
         *
         * @return 本地存储配置
         */
        public Local getLocal() {
            return local;
        }

        /**
         * 获取 FTP 与 FTPS 配置。
         *
         * @return FTP 配置
         */
        public Ftp getFtp() {
            return ftp;
        }
    }

    /**
     * 本地文件系统配置。
     */
    public static class Local {
        private String basePath = System.getProperty("user.home") + "/letool/files";

        /**
         * 获取本地存储根目录。
         *
         * @return 本地存储根目录
         */
        public String getBasePath() {
            return basePath;
        }

        /**
         * 设置本地存储根目录。
         *
         * @param basePath 本地存储根目录
         */
        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }

    /**
     * FTP 与 FTPS 连接配置。
     */
    public static class Ftp {
        private String host = "localhost";
        private int port = 21;
        private String username;
        private String password;
        private String basePath = "/";
        private String charset = "UTF-8";
        private boolean passiveMode = true;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration defaultTimeout = Duration.ofSeconds(10);
        private Duration dataTimeout = Duration.ofSeconds(30);
        private Duration keepAliveInterval = Duration.ofSeconds(30);
        private String protocol = "TLS";
        private boolean implicit;
        private boolean endpointCheckingEnabled = true;

        /** @return FTP 服务器地址 */
        public String getHost() { return host; }

        /** @param host FTP 服务器地址 */
        public void setHost(String host) { this.host = host; }

        /** @return FTP 服务器端口 */
        public int getPort() { return port; }

        /** @param port FTP 服务器端口 */
        public void setPort(int port) { this.port = port; }

        /** @return FTP 登录用户名 */
        public String getUsername() { return username; }

        /** @param username FTP 登录用户名 */
        public void setUsername(String username) { this.username = username; }

        /** @return FTP 登录密码 */
        public String getPassword() { return password; }

        /** @param password FTP 登录密码 */
        public void setPassword(String password) { this.password = password; }

        /** @return 远程存储根目录 */
        public String getBasePath() { return basePath; }

        /** @param basePath 远程存储根目录 */
        public void setBasePath(String basePath) { this.basePath = basePath; }

        /** @return FTP 控制连接字符集 */
        public String getCharset() { return charset; }

        /** @param charset FTP 控制连接字符集 */
        public void setCharset(String charset) { this.charset = charset; }

        /** @return 是否使用被动模式 */
        public boolean isPassiveMode() { return passiveMode; }

        /** @param passiveMode 是否使用被动模式 */
        public void setPassiveMode(boolean passiveMode) { this.passiveMode = passiveMode; }

        /** @return 建立连接超时 */
        public Duration getConnectTimeout() { return connectTimeout; }

        /** @param connectTimeout 建立连接超时 */
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

        /** @return 控制连接默认超时 */
        public Duration getDefaultTimeout() { return defaultTimeout; }

        /** @param defaultTimeout 控制连接默认超时 */
        public void setDefaultTimeout(Duration defaultTimeout) { this.defaultTimeout = defaultTimeout; }

        /** @return 数据连接超时 */
        public Duration getDataTimeout() { return dataTimeout; }

        /** @param dataTimeout 数据连接超时 */
        public void setDataTimeout(Duration dataTimeout) { this.dataTimeout = dataTimeout; }

        /** @return 控制连接保活间隔 */
        public Duration getKeepAliveInterval() { return keepAliveInterval; }

        /** @param keepAliveInterval 控制连接保活间隔 */
        public void setKeepAliveInterval(Duration keepAliveInterval) { this.keepAliveInterval = keepAliveInterval; }

        /** @return FTPS 协议名称 */
        public String getProtocol() { return protocol; }

        /** @param protocol FTPS 协议名称 */
        public void setProtocol(String protocol) { this.protocol = protocol; }

        /** @return 是否使用隐式 FTPS */
        public boolean isImplicit() { return implicit; }

        /** @param implicit 是否使用隐式 FTPS */
        public void setImplicit(boolean implicit) { this.implicit = implicit; }

        /** @return 是否启用 TLS 端点校验 */
        public boolean isEndpointCheckingEnabled() { return endpointCheckingEnabled; }

        /** @param endpointCheckingEnabled 是否启用 TLS 端点校验 */
        public void setEndpointCheckingEnabled(boolean endpointCheckingEnabled) {
            this.endpointCheckingEnabled = endpointCheckingEnabled;
        }
    }

    /**
     * ZIP 解压安全限制。
     */
    public static class Archive {
        private int maxEntries = 10_000;
        private DataSize maxEntrySize = DataSize.ofMegabytes(100);
        private DataSize maxTotalSize = DataSize.ofGigabytes(1);

        /** @return 最大条目数量 */
        public int getMaxEntries() { return maxEntries; }

        /** @param maxEntries 最大条目数量 */
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }

        /** @return 单条目最大解压大小 */
        public DataSize getMaxEntrySize() { return maxEntrySize; }

        /** @param maxEntrySize 单条目最大解压大小 */
        public void setMaxEntrySize(DataSize maxEntrySize) { this.maxEntrySize = maxEntrySize; }

        /** @return 全部条目最大解压大小 */
        public DataSize getMaxTotalSize() { return maxTotalSize; }

        /** @param maxTotalSize 全部条目最大解压大小 */
        public void setMaxTotalSize(DataSize maxTotalSize) { this.maxTotalSize = maxTotalSize; }
    }
}
