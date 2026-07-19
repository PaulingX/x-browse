package com.xbrowse.service;

import com.xbrowse.entity.AlistEngine;
import com.xbrowse.repository.AlistEngineRepository;
import com.xbrowse.util.AlistClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * 目录预览图本地缓存服务
 * <p>
 * 功能说明：
 * 1. 同步时将目录的第一个图片文件下载到本地缓存目录
 * 2. 浏览目录时直接从本地缓存读取预览图，避免每次都代理请求 Alist
 * 3. 缓存目录结构：{cacheDir}/thumbnails/{engineId}/{pathHash}.{ext}
 */
@Slf4j
@Service
public class ThumbnailCacheService {

    @Value("${xbrowse.cache-dir:./data/cache}")
    private String cacheDir;

    /** 缓存根目录下的缩略图子目录名 */
    private static final String THUMBNAILS_DIR = "thumbnails";

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
     * @param engineId    引擎 ID
     * @param imagePath   图片文件的完整路径（如 /pve/back/漫画/封面.jpg）
     * @param client      Alist 客户端（复用连接，避免重复创建）
     * @return 本地缓存文件的相对 URL 路径（如 /api/files/thumbnail/1/xxx.jpg），失败返回 null
     */
    public String cacheThumbnail(Long engineId, String imagePath, AlistClient client) {
        log.info("缓存目录预览图: engineId={}, imagePath={}", engineId, imagePath);

        // 获取文件扩展名
        String ext = getExtension(imagePath);
        if (ext.isEmpty()) {
            log.warn("预览图文件无扩展名，跳过缓存: engineId={}, imagePath={}", engineId, imagePath);
            return null;
        }

        // 生成缓存文件路径（使用路径哈希避免文件名冲突和路径过长问题）
        String pathHash = md5(imagePath);
        String cacheFileName = pathHash + "." + ext;
        Path cachePath = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheFileName);

        // 如果已缓存，直接返回 URL
        if (Files.exists(cachePath)) {
            log.debug("预览图已缓存，跳过下载: engineId={}, cachePath={}", engineId, cachePath);
            return buildThumbnailUrl(engineId, cacheFileName);
        }

        try {
            // 从 Alist 获取文件下载地址
            String fileUrl = client.getFileUrl(imagePath);
            if (fileUrl == null) {
                log.warn("获取预览图下载地址失败: engineId={}, imagePath={}", engineId, imagePath);
                return null;
            }

            // 创建引擎子目录
            Path engineDir = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId));
            Files.createDirectories(engineDir);

            // 下载并保存到本地
            downloadToFile(fileUrl, cachePath);
            log.info("预览图缓存成功: engineId={}, imagePath={}, cachePath={}", engineId, imagePath, cachePath);

            return buildThumbnailUrl(engineId, cacheFileName);
        } catch (Exception e) {
            log.error("缓存预览图失败: engineId={}, imagePath={}", engineId, imagePath, e);
            // 清理可能的不完整文件
            try { Files.deleteIfExists(cachePath); } catch (IOException ignored) {}
            return null;
        }
    }

    /**
     * 获取本地缓存文件的完整路径
     *
     * @param engineId   引擎 ID
     * @param cacheName  缓存文件名（含扩展名）
     * @return 本地文件 Path，不存在返回 null
     */
    public Path getCachedThumbnailPath(Long engineId, String cacheName) {
        Path path = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheName);
        return Files.exists(path) ? path : null;
    }

    /**
     * 检查预览图是否已缓存
     *
     * @param engineId  引擎 ID
     * @param imagePath 图片文件完整路径
     * @return 是否已缓存
     */
    public boolean isCached(Long engineId, String imagePath) {
        String ext = getExtension(imagePath);
        if (ext.isEmpty()) return false;
        String pathHash = md5(imagePath);
        Path cachePath = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), pathHash + "." + ext);
        return Files.exists(cachePath);
    }

    /**
     * 构建缩略图访问 URL
     */
    private String buildThumbnailUrl(Long engineId, String cacheFileName) {
        return "/api/files/thumbnail/" + engineId + "/" + cacheFileName;
    }

    /**
     * 从 HTTP URL 下载文件到本地路径
     */
    private void downloadToFile(String fileUrl, Path targetPath) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 从文件路径提取扩展名（小写）
     */
    private String getExtension(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        return path.substring(dot + 1).toLowerCase();
    }

    /**
     * 计算字符串的 MD5 哈希值
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // MD5 算法不可用时回退到简单哈希
            return String.valueOf(input.hashCode());
        }
    }
}
