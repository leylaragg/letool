package com.github.leyland.letool.file.storage;

import com.github.leyland.letool.file.config.FileProperties;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Apache Commons Net 的 FTP 文件存储实现。
 *
 * <p>每次操作（上传/下载/删除/列表）都会建立新的 FTP 连接，操作完成后自动断开。
 * 支持被动模式（推荐，可穿透防火墙）和二进制传输，通过 {@link FileProperties.Ftp}
 * 配置连接参数。</p>
 *
 * <p><b>上传流程：</b></p>
 * <ol>
 *   <li>建立 FTP 连接并登录</li>
 *   <li>逐级创建目标目录（若不存在）</li>
 *   <li>设置二进制传输模式</li>
 *   <li>上传文件流</li>
 *   <li>断开连接</li>
 * </ol>
 *
 * <p><b>下载流程：</b></p>
 * <ol>
 *   <li>建立 FTP 连接并登录</li>
 *   <li>设置二进制传输模式</li>
 *   <li>读取远程文件到内存字节数组</li>
 *   <li>返回 ByteArrayInputStream 供调用方使用</li>
 *   <li>断开连接</li>
 * </ol>
 *
 * @author leyland
 * @since 1.0.0
 */
public class FtpFileStorage implements FileStorageProvider {

    private static final Logger log = LoggerFactory.getLogger(FtpFileStorage.class);

    /** FTP 服务器地址 */
    private final String host;

    /** FTP 服务器端口 */
    private final int port;

    /** 登录用户名 */
    private final String username;

    /** 登录密码 */
    private final String password;

    /** 是否使用被动模式 */
    private final boolean passiveMode;

    /** 连接超时时间（毫秒） */
    private final int connectTimeout;

    /**
     * 根据 FTP 配置构造存储实例。
     *
     * @param ftpProps FTP 配置属性对象
     */
    public FtpFileStorage(FileProperties.Ftp ftpProps) {
        this.host = ftpProps.getHost();
        this.port = ftpProps.getPort();
        this.username = ftpProps.getUsername();
        this.password = ftpProps.getPassword();
        this.passiveMode = ftpProps.isPassiveMode();
        this.connectTimeout = ftpProps.getConnectTimeout();
    }

    // ===== 文件上传 =====

    /**
     * 上传文件到 FTP 服务器。
     *
     * <p>目标目录不存在时自动创建，使用二进制模式传输以保证文件完整性。</p>
     *
     * @param inputStream 文件内容的输入流
     * @param path        目标远程目录路径
     * @param fileName    目标文件名
     * @return 文件在 FTP 服务器上的完整路径
     * @throws UncheckedIOException 上传失败时抛出
     */
    @Override
    public String upload(InputStream inputStream, String path, String fileName) {
        FTPClient ftp = connect();
        try {
            createDirectories(ftp, path);
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            boolean ok = ftp.storeFile(path + "/" + fileName, inputStream);
            if (!ok) {
                throw new IOException("FTP upload failed: " + ftp.getReplyString());
            }
            log.debug("File uploaded via FTP: {}/{}", path, fileName);
            return path + "/" + fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("FTP upload failed: " + path + "/" + fileName, e);
        } finally {
            disconnect(ftp);
        }
    }

    // ===== 文件下载 =====

    /**
     * 从 FTP 服务器下载文件。
     *
     * <p>由于 FTP 连接在方法返回前关闭，文件内容会先读取到内存中再返回。
     * 对于大文件建议改用流式传输方案。</p>
     *
     * @param path 远程文件路径
     * @return 文件内容的输入流（ByteArrayInputStream）
     * @throws UncheckedIOException 下载失败时抛出
     */
    @Override
    public InputStream download(String path) {
        FTPClient ftp = connect();
        try {
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean ok = ftp.retrieveFile(path, baos);
            if (!ok) {
                throw new IOException("FTP download failed: " + ftp.getReplyString());
            }
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("FTP download failed: " + path, e);
        } finally {
            disconnect(ftp);
        }
    }

    // ===== 文件删除 =====

    /**
     * 删除 FTP 服务器上的指定文件。
     *
     * @param path 远程文件路径
     * @return {@code true} 删除成功，{@code false} 文件不存在或删除失败
     * @throws UncheckedIOException 删除操作发生 I/O 错误时抛出
     */
    @Override
    public boolean delete(String path) {
        FTPClient ftp = connect();
        try {
            return ftp.deleteFile(path);
        } catch (IOException e) {
            throw new UncheckedIOException("FTP delete failed: " + path, e);
        } finally {
            disconnect(ftp);
        }
    }

    // ===== 存在性检查 =====

    /**
     * 检查文件是否存在于 FTP 服务器。
     *
     * <p>通过列出该路径的父目录并检查返回结果来判断文件是否存在。</p>
     *
     * @param path 远程文件路径
     * @return {@code true} 文件存在，{@code false} 不存在或发生异常
     */
    @Override
    public boolean exists(String path) {
        FTPClient ftp = connect();
        try {
            FTPFile[] files = ftp.listFiles(path);
            return files != null && files.length > 0;
        } catch (IOException e) {
            return false;
        } finally {
            disconnect(ftp);
        }
    }

    // ===== 目录列表 =====

    /**
     * 列出 FTP 服务器上指定目录的文件和子目录。
     *
     * @param path 远程目录路径
     * @return 文件/目录信息列表，目录为空时返回空列表
     * @throws UncheckedIOException 列表读取失败时抛出
     */
    @Override
    public List<FileInfo> list(String path) {
        List<FileInfo> result = new ArrayList<>();
        FTPClient ftp = connect();
        try {
            FTPFile[] files = ftp.listFiles(path);
            if (files != null) {
                for (FTPFile f : files) {
                    result.add(new FileInfo(
                            f.getName(), path + "/" + f.getName(),
                            f.getSize(), f.isDirectory(),
                            f.getTimestamp().getTimeInMillis()
                    ));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("FTP list failed: " + path, e);
        } finally {
            disconnect(ftp);
        }
        return result;
    }

    // ===== 内部连接管理 =====

    /**
     * 建立 FTP 连接并登录。
     *
     * <p>连接后根据配置决定是否进入被动模式。</p>
     *
     * @return 已登录的 FTPClient 实例
     * @throws UncheckedIOException 连接或登录失败时抛出
     */
    private FTPClient connect() {
        try {
            FTPClient ftp = new FTPClient();
            ftp.setConnectTimeout(connectTimeout);
            ftp.connect(host, port);
            if (!ftp.login(username, password)) {
                throw new IOException("FTP login failed: " + ftp.getReplyString());
            }
            if (passiveMode) {
                ftp.enterLocalPassiveMode();
            }
            return ftp;
        } catch (IOException e) {
            throw new UncheckedIOException("FTP connect failed: " + host + ":" + port, e);
        }
    }

    /**
     * 安全断开 FTP 连接，静默处理任何异常。
     *
     * @param ftp 要断开的 FTPClient 实例
     */
    private void disconnect(FTPClient ftp) {
        try {
            if (ftp.isConnected()) {
                ftp.logout();
                ftp.disconnect();
            }
        } catch (IOException ignored) {
            // 断开连接时的异常不影响业务逻辑，静默忽略
        }
    }

    /**
     * 在 FTP 服务器上逐级创建目录。
     *
     * <p>从根路径开始，依次进入/创建各层级目录，创建完成后回到根目录。</p>
     *
     * @param ftp  已连接的 FTPClient 实例
     * @param path 要创建的目录路径（以 / 分隔）
     * @throws IOException 目录创建失败时抛出
     */
    private void createDirectories(FTPClient ftp, String path) throws IOException {
        for (String dir : path.split("/")) {
            if (dir.isEmpty()) continue;
            if (!ftp.changeWorkingDirectory(dir)) {
                ftp.makeDirectory(dir);
                ftp.changeWorkingDirectory(dir);
            }
        }
        ftp.changeWorkingDirectory("/");
    }
}
