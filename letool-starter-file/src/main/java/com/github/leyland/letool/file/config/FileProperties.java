package com.github.leyland.letool.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件模块配置属性类，对应 YAML 中的 {@code letool.file} 前缀。
 *
 * <p>该配置类聚合了上传限制、存储类型、本地文件系统、FTP/SFTP 远程存储等所有配置项。
 * 使用者可在 {@code application.yml} 中按如下结构配置：</p>
 *
 * <pre>{@code
 * letool:
 *   file:
 *     enabled: true           # 是否启用文件模块
 *     upload:
 *       max-size: 10MB        # 上传文件最大体积
 *       allowed-types:        # 允许的文件扩展名
 *         - jpg
 *         - png
 *         - pdf
 *       storage-path: /data/uploads
 *     storage:
 *       type: local           # 存储类型：local / ftp / sftp
 *       local:
 *         base-path: /var/files
 *       ftp:
 *         host: 192.168.1.100
 *         port: 21
 *         username: admin
 *         password: secret
 * }</pre>
 *
 * @author leyland
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "letool.file")
public class FileProperties {

    // ===== 顶层属性 =====

    /** 上传相关的全局配置 */
    private Upload upload = new Upload();

    /** 存储类型及各类存储的具体配置 */
    private Storage storage = new Storage();

    /** 是否启用文件模块，默认 true */
    private boolean enabled = true;

    // ===== Getter / Setter =====

    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // ===== 上传配置内嵌类 =====

    /**
     * 文件上传的通用配置。
     *
     * <p>控制上传文件的大小上限、允许的扩展名白名单，以及上传后的存储目录。</p>
     */
    public static class Upload {

        /** 单文件最大体积，支持 10MB、1GB 等格式，默认 10MB */
        private String maxSize = "10MB";

        /** 允许上传的文件扩展名白名单，为空表示不限制 */
        private String[] allowedTypes;

        /** 上传文件的临时/目标存储路径，默认 /data/uploads */
        private String storagePath = "/data/uploads";

        // ---- Getter / Setter ----

        public String getMaxSize() { return maxSize; }
        public void setMaxSize(String maxSize) { this.maxSize = maxSize; }

        public String[] getAllowedTypes() { return allowedTypes; }
        public void setAllowedTypes(String[] allowedTypes) { this.allowedTypes = allowedTypes; }

        public String getStoragePath() { return storagePath; }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    }

    // ===== 存储配置内嵌类 =====

    /**
     * 存储总配置，包含存储类型及各类存储的参数。
     *
     * <p>{@code type} 决定使用哪种存储实现：
     * <ul>
     *   <li>{@code local} — 本地文件系统</li>
     *   <li>{@code ftp} — FTP 远程服务器</li>
     *   <li>{@code sftp} — SFTP 远程服务器（预留）</li>
     * </ul>
     * 同时对每种存储类型提供独立配置子对象，未启用的存储类型可忽略。
     */
    public static class Storage {

        /** 存储类型：local / ftp / sftp，默认 local */
        private String type = "local";

        /** 本地存储配置 */
        private Local local = new Local();

        /** FTP 存储配置 */
        private Ftp ftp = new Ftp();

        /** SFTP 存储配置（预留） */
        private Sftp sftp = new Sftp();

        /** 扩展属性，用于极少量自定义配置 */
        private Map<String, String> extra = new HashMap<>();

        // ---- Getter / Setter ----

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Local getLocal() { return local; }
        public void setLocal(Local local) { this.local = local; }

        public Ftp getFtp() { return ftp; }
        public void setFtp(Ftp ftp) { this.ftp = ftp; }

        public Sftp getSftp() { return sftp; }
        public void setSftp(Sftp sftp) { this.sftp = sftp; }

        public Map<String, String> getExtra() { return extra; }
        public void setExtra(Map<String, String> extra) { this.extra = extra; }
    }

    // ===== 本地存储配置 =====

    /**
     * 本地文件系统存储配置。
     *
     * <p>所有文件存储在 {@code basePath} 指定的本地目录下。</p>
     */
    public static class Local {

        /** 本地存储根目录，默认 ~/letool/files */
        private String basePath = System.getProperty("user.home") + "/letool/files";

        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
    }

    // ===== FTP 存储配置 =====

    /**
     * FTP 远程存储配置。
     *
     * <p>基于 Apache Commons Net 实现，支持主动/被动模式切换和连接超时设置。</p>
     */
    public static class Ftp {

        /** FTP 服务器地址，默认 localhost */
        private String host = "localhost";

        /** FTP 端口，默认 21 */
        private int port = 21;

        /** FTP 登录用户名 */
        private String username;

        /** FTP 登录密码 */
        private String password;

        /** 是否使用被动模式，默认 true（适配防火墙环境） */
        private boolean passiveMode = true;

        /** 连接超时时间（毫秒），默认 10000 */
        private int connectTimeout = 10000;

        // ---- Getter / Setter ----

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public boolean isPassiveMode() { return passiveMode; }
        public void setPassiveMode(boolean passiveMode) { this.passiveMode = passiveMode; }

        public int getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    }

    // ===== SFTP 存储配置 =====

    /**
     * SFTP 远程存储配置（预留）。
     *
     * <p>支持密码认证或私钥文件认证两种方式。</p>
     */
    public static class Sftp {

        /** SFTP 服务器地址，默认 localhost */
        private String host = "localhost";

        /** SFTP 端口，默认 22 */
        private int port = 22;

        /** SFTP 登录用户名 */
        private String username;

        /** SFTP 登录密码（与 privateKeyPath 二选一） */
        private String password;

        /** SSH 私钥文件路径（与 password 二选一） */
        private String privateKeyPath;

        /** 连接超时时间（毫秒），默认 10000 */
        private int connectTimeout = 10000;

        // ---- Getter / Setter ----

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

        public int getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    }
}
