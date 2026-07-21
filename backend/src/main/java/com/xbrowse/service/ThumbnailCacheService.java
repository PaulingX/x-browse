package com.xbrowse.service;

import com.xbrowse.util.AlistClient;
import com.xbrowse.util.MediaTypes;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

/**
 * 目录预览图本地缓存服务
 * <p>
 * 功能说明：
 * 1. 同步时将目录的第一个图片文件下载到本地缓存目录
 * 2. 浏览目录时直接从本地缓存读取预览图，避免每次都代理请求 Alist
 * 3. 缓存目录结构：{cacheDir}/thumbnails/{engineId}/{pathHash}.jpg
 */
@Slf4j
@Service
public class ThumbnailCacheService {

    /** 缓存根目录下的缩略图子目录名 */
    private static final String THUMBNAILS_DIR = "thumbnails";

    /** 缩略图最大边长（像素） */
    private static final int MAX_THUMB_WIDTH = 200;

    /** JPEG 输出质量 */
    private static final double JPEG_QUALITY = 0.7;

    /** 下载读超时 */
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60_000;

    @Value("${xbrowse.cache-dir:./data/cache}")
    private String cacheDir;

    /**
     * 初始化缓存目录
     */
    @PostConstruct
    public void init() {
        try {
            Path thumbDir = Paths.get(cacheDir, THUMBNAILS_DIR);
            Files.createDirectories(thumbDir);
            log.info("缩略图缓存目录初始化完成: {}", thumbDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("创建缩览图缓存目录失败: {}", cacheDir, e);
        }
    }

    /**
     * 缓存目录预览图到本地磁盘
     *
     * @param engineId  引擎 ID
     * @param imagePath 图片文件的完整路径（如 /相册/封面.jpg）
     * @param client    Alist 客户端（复用连接）
     * @return 本地缓存 URL（如 /api/files/thumbnail/1/xxx.jpg），失败返回 null
     */
    public String cacheThumbnail(Long engineId, String imagePath, AlistClient client) {
        log.info("缓存目录预览图: engineId={}, imagePath={}", engineId, imagePath);
        // 使用路径哈希避免文件名冲突和路径过长
        String cacheFileName = md5(imagePath) + ".jpg";
        Path cachePath = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheFileName);

        // 已缓存则直接返回
        if (Files.exists(cachePath)) {
            return buildThumbnailUrl(engineId, cacheFileName);
        }

        try {
            String fileUrl = client.getFileUrl(imagePath);
            if (fileUrl == null) {
                log.warn("获取预览图下载地址失败: engineId={}, imagePath={}", engineId, imagePath);
                return null;
            }
            Files.createDirectories(cachePath.getParent());
            downloadToFile(fileUrl, cachePath);
            log.info("预览图缓存成功: engineId={}, imagePath={}, cachePath={}", engineId, imagePath, cachePath);
            return buildThumbnailUrl(engineId, cacheFileName);
        } catch (Exception e) {
            log.error("缓存预览图失败: engineId={}, imagePath={}", engineId, imagePath, e);
            // 清理可能的不完整文件
            try {
                Files.deleteIfExists(cachePath);
            } catch (IOException ignored) {
                // 忽略清理失败
            }
            return null;
        }
    }

    /**
     * 获取本地缓存文件的完整路径
     *
     * @param engineId  引擎 ID
     * @param cacheName 缓存文件名（含扩展名）
     * @return 本地文件 Path，不存在返回 null
     */
    public Path getCachedThumbnailPath(Long engineId, String cacheName) {
        Path path = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheName);
        return Files.exists(path) ? path : null;
    }

    /**
     * 构建缩略图访问 URL
     */
    private String buildThumbnailUrl(Long engineId, String cacheFileName) {
        return "/api/files/thumbnail/" + engineId + "/" + cacheFileName;
    }

    /**
     * 从 HTTP URL 下载图片并压缩为缩略图保存到本地
     * <p>
     * 等比缩放最大边 200px，输出 JPEG；无法解码时按原文件保存。
     */
    private void downloadToFile(String fileUrl, Path targetPath) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(MediaTypes.connectTimeoutMs());
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            byte[] data = in.readAllBytes();
            try {
                Thumbnails.of(new java.io.ByteArrayInputStream(data))
                        .size(MAX_THUMB_WIDTH, MAX_THUMB_WIDTH)
                        .keepAspectRatio(true)
                        .outputFormat("jpg")
                        .outputQuality(JPEG_QUALITY)
                        .toFile(targetPath.toFile());
            } catch (Exception e) {
                log.warn("无法解码图片，按原文件保存: {}", fileUrl);
                Files.write(targetPath, data);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 计算字符串的 MD5 哈希值（用于缓存文件名）
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // MD5 不可用时回退简单哈希
            return String.valueOf(input.hashCode());
        }
    }
}
