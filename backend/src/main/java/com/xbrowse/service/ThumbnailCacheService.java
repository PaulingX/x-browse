package com.xbrowse.service;

import com.xbrowse.util.AlistClient;
import com.xbrowse.util.MediaTypes;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
 * 图片本地缓存服务
 * <p>
 * 1. 同步时生成列表缩略图（小 JPEG）
 * 2. 可选缓存 proxy 原图，减轻重复代理
 * 3. 目录结构：
 *    {cacheDir}/thumbnails/{engineId}/{pathHash}.jpg
 *    {cacheDir}/proxy/{engineId}/{pathHash}.{ext}
 */
@Slf4j
@Service
public class ThumbnailCacheService {

    private static final String THUMBNAILS_DIR = "thumbnails";
    private static final String PROXY_DIR = "proxy";
    private static final double JPEG_QUALITY = 0.72;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60_000;

    @Value("${xbrowse.cache-dir:./data/cache}")
    private String cacheDir;

    @Value("${xbrowse.thumbnail-enabled:true}")
    private boolean thumbnailEnabled;

    @Value("${xbrowse.thumbnail-max-width:200}")
    private int maxThumbWidth;

    @Getter
    @Value("${xbrowse.proxy-cache-enabled:true}")
    private boolean proxyCacheEnabled;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(cacheDir, THUMBNAILS_DIR));
            Files.createDirectories(Paths.get(cacheDir, PROXY_DIR));
            log.info("图片缓存目录初始化完成: {}", Paths.get(cacheDir).toAbsolutePath());
        } catch (IOException e) {
            log.error("创建图片缓存目录失败: {}", cacheDir, e);
        }
    }

    /**
     * 缓存列表缩略图（最大边 maxThumbWidth）
     *
     * @return 本地缓存 URL，失败返回 null
     */
    public String cacheThumbnail(Long engineId, String imagePath, AlistClient client) {
        if (!thumbnailEnabled || engineId == null || imagePath == null) {
            return null;
        }
        String cacheFileName = md5(imagePath) + ".jpg";
        Path cachePath = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheFileName);
        if (Files.exists(cachePath)) {
            return buildThumbnailUrl(engineId, cacheFileName);
        }
        try {
            String fileUrl = client.getFileUrl(imagePath);
            if (fileUrl == null) {
                return null;
            }
            Files.createDirectories(cachePath.getParent());
            downloadAndResize(fileUrl, cachePath, maxThumbWidth);
            return buildThumbnailUrl(engineId, cacheFileName);
        } catch (Exception e) {
            log.warn("缓存缩略图失败: engineId={}, path={}, err={}", engineId, imagePath, e.getMessage());
            try {
                Files.deleteIfExists(cachePath);
            } catch (IOException ignored) {
                // ignore
            }
            return null;
        }
    }

    /**
     * 获取本地缩略图路径
     */
    public Path getCachedThumbnailPath(Long engineId, String cacheName) {
        Path path = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheName);
        return Files.exists(path) ? path : null;
    }

    /**
     * 获取 proxy 原图本地缓存（若存在）
     */
    public Path getCachedProxyPath(Long engineId, String filePath) {
        if (!proxyCacheEnabled || engineId == null || filePath == null) {
            return null;
        }
        Path path = proxyCacheFile(engineId, filePath);
        return Files.exists(path) ? path : null;
    }

    /**
     * 将上游流写入 proxy 本地缓存，并返回缓存路径；失败返回 null
     */
    public Path cacheProxyFile(Long engineId, String filePath, InputStream in) {
        if (!proxyCacheEnabled || engineId == null || filePath == null || in == null) {
            return null;
        }
        Path target = proxyCacheFile(engineId, filePath);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (Exception e) {
            log.warn("缓存 proxy 文件失败: engineId={}, path={}, err={}", engineId, filePath, e.getMessage());
            try {
                Files.deleteIfExists(target);
                Files.deleteIfExists(target.resolveSibling(target.getFileName() + ".tmp"));
            } catch (IOException ignored) {
                // ignore
            }
            return null;
        }
    }

    private Path proxyCacheFile(Long engineId, String filePath) {
        String ext = MediaTypes.extensionOf(filePath);
        if (ext == null || ext.isEmpty()) {
            ext = "bin";
        }
        return Paths.get(cacheDir, PROXY_DIR, String.valueOf(engineId), md5(filePath) + "." + ext);
    }

    private String buildThumbnailUrl(Long engineId, String cacheFileName) {
        return "/api/files/thumbnail/" + engineId + "/" + cacheFileName;
    }

    /**
     * 从 HTTP URL 下载图片并压缩为缩略图保存到本地
     * <p>
     * 等比缩放最大边 200px，输出 JPEG；无法解码时按原文件保存。
     */
    private void downloadAndResize(String fileUrl, Path targetPath, int maxWidth) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(MediaTypes.connectTimeoutMs());
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            byte[] data = in.readAllBytes();
            try {
                Thumbnails.of(new java.io.ByteArrayInputStream(data))
                        .size(maxWidth, maxWidth)
                        .keepAspectRatio(true)
                        .outputFormat("jpg")
                        .outputQuality(JPEG_QUALITY)
                        .toFile(targetPath.toFile());
            } catch (Exception e) {
                // 无法解码时保存原文件（可能不是标准位图）
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
