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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 图片本地缓存服务
 * <p>
 * 1. 同步时生成列表缩略图（小 JPEG）
 * 2. 可选缓存 proxy 原图，减轻重复代理
 * 3. 目录结构：
 *    {cacheDir}/thumbnails/{engineId}/{pathHash}.jpg
 *    {cacheDir}/proxy/{engineId}/{pathHash}.{ext}
 * <p>
 * 并发安全：
 * - 分片锁按目标文件路径加锁，避免同文件双写
 * - 全局信号量限制同时写盘并发，避免多定时任务把磁盘/网络打满
 * - 先写 .tmp 再 replace，避免半截文件
 */
@Slf4j
@Service
public class ThumbnailCacheService {

    private static final String THUMBNAILS_DIR = "thumbnails";
    private static final String PROXY_DIR = "proxy";
    private static final double JPEG_QUALITY = 0.72;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60_000;
    /** 同时写缓存上限（缩略图 + proxy） */
    private static final int MAX_CACHE_WRITERS = 2;
    private static final long LOCK_WAIT_MS = 30_000L;
    private static final int STRIPE_COUNT = 64;

    @Value("${xbrowse.cache-dir:./data/cache}")
    private String cacheDir;

    @Value("${xbrowse.thumbnail-enabled:true}")
    private boolean thumbnailEnabled;

    @Value("${xbrowse.thumbnail-max-width:200}")
    private int maxThumbWidth;

    @Getter
    @Value("${xbrowse.proxy-cache-enabled:false}")
    private boolean proxyCacheEnabled;

    private final Semaphore writeLimiter = new Semaphore(MAX_CACHE_WRITERS, true);
    private final Object[] fileStripes = new Object[STRIPE_COUNT];

    public ThumbnailCacheService() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            fileStripes[i] = new Object();
        }
    }

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
     * <p>
     * 压缩生成失败时回退为保存原图，仍返回缓存 URL，保证列表有图可显示。
     *
     * @return 本地缓存 URL，仅下载完全失败时返回 null
     */
    public String cacheThumbnail(Long engineId, String imagePath, AlistClient client) {
        if (!thumbnailEnabled || engineId == null || imagePath == null) {
            return null;
        }
        String cacheFileName = md5(imagePath) + ".jpg";
        Path cachePath = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheFileName);
        String url = buildThumbnailUrl(engineId, cacheFileName);
        if (Files.exists(cachePath) && isNonEmptyFile(cachePath)) {
            return url;
        }

        Object lock = stripeLock(cachePath);
        synchronized (lock) {
            if (Files.exists(cachePath) && isNonEmptyFile(cachePath)) {
                return url;
            }
            boolean acquired = false;
            try {
                acquired = writeLimiter.tryAcquire(LOCK_WAIT_MS, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    log.warn("获取缩略图写锁超时，跳过: engineId={}, path={}", engineId, imagePath);
                    return null;
                }
                if (Files.exists(cachePath) && isNonEmptyFile(cachePath)) {
                    return url;
                }
                String fileUrl = client.getFileUrl(imagePath);
                if (fileUrl == null) {
                    return null;
                }
                Files.createDirectories(cachePath.getParent());
                boolean ok = downloadAndResizeAtomic(fileUrl, cachePath, maxThumbWidth);
                if (ok && isNonEmptyFile(cachePath)) {
                    return url;
                }
                log.warn("缩略图写入无效，放弃: engineId={}, path={}", engineId, imagePath);
                deleteQuietly(cachePath);
                return null;
            } catch (Exception e) {
                log.warn("缓存缩略图失败: engineId={}, path={}, err={}", engineId, imagePath, e.getMessage());
                deleteQuietly(cachePath);
                deleteQuietly(tmpPath(cachePath));
                return null;
            } finally {
                if (acquired) {
                    writeLimiter.release();
                }
            }
        }
    }

    public Path getCachedThumbnailPath(Long engineId, String cacheName) {
        Path path = Paths.get(cacheDir, THUMBNAILS_DIR, String.valueOf(engineId), cacheName);
        return Files.exists(path) ? path : null;
    }

    public Path getCachedProxyPath(Long engineId, String filePath) {
        if (!proxyCacheEnabled || engineId == null || filePath == null) {
            return null;
        }
        Path path = proxyCacheFile(engineId, filePath);
        return Files.exists(path) ? path : null;
    }

    /**
     * 将上游流写入 proxy 本地缓存；失败返回 null
     */
    public Path cacheProxyFile(Long engineId, String filePath, InputStream in) {
        if (!proxyCacheEnabled || engineId == null || filePath == null || in == null) {
            return null;
        }
        Path target = proxyCacheFile(engineId, filePath);
        if (Files.exists(target)) {
            return target;
        }

        Object lock = stripeLock(target);
        synchronized (lock) {
            if (Files.exists(target)) {
                return target;
            }
            boolean acquired = false;
            try {
                acquired = writeLimiter.tryAcquire(LOCK_WAIT_MS, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    log.warn("获取 proxy 写锁超时，跳过缓存: engineId={}, path={}", engineId, filePath);
                    return null;
                }
                if (Files.exists(target)) {
                    return target;
                }
                Files.createDirectories(target.getParent());
                Path tmp = tmpPath(target);
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                moveReplace(tmp, target);
                return target;
            } catch (Exception e) {
                log.warn("缓存 proxy 文件失败: engineId={}, path={}, err={}", engineId, filePath, e.getMessage());
                deleteQuietly(target);
                deleteQuietly(tmpPath(target));
                return null;
            } finally {
                if (acquired) {
                    writeLimiter.release();
                }
            }
        }
    }

    private Object stripeLock(Path path) {
        int idx = Math.floorMod(path.toString().hashCode(), STRIPE_COUNT);
        return fileStripes[idx];
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
     * 下载原图并尝试压缩；压缩失败则将原图原样写入缓存。
     *
     * @return 是否成功得到可用缓存文件
     */
    private boolean downloadAndResizeAtomic(String fileUrl, Path targetPath, int maxWidth) throws IOException {
        Path tmp = tmpPath(targetPath);
        HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(MediaTypes.connectTimeoutMs());
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
            byte[] data = in.readAllBytes();
            if (data == null || data.length == 0) {
                return false;
            }
            boolean resized = writeResizedJpeg(data, tmp, maxWidth);
            if (!resized) {
                // 生成缩略图失败：直接存原图作为缓存
                log.info("缩略图生成失败，回退保存原图: size={}B, target={}", data.length, targetPath.getFileName());
                Files.write(tmp, data);
            }
            if (!isNonEmptyFile(tmp)) {
                return false;
            }
            moveReplace(tmp, targetPath);
            return isNonEmptyFile(targetPath);
        } finally {
            connection.disconnect();
            deleteQuietly(tmp);
        }
    }

    /**
     * 尝试压缩为 JPEG 缩略图；失败返回 false（由调用方改存原图）
     */
    private boolean writeResizedJpeg(byte[] data, Path tmp, int maxWidth) {
        try {
            Thumbnails.of(new java.io.ByteArrayInputStream(data))
                    .size(maxWidth, maxWidth)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(JPEG_QUALITY)
                    .toFile(tmp.toFile());
            return isNonEmptyFile(tmp);
        } catch (Exception e) {
            log.debug("图片压缩失败: {}", e.getMessage());
            deleteQuietly(tmp);
            return false;
        }
    }

    private boolean isNonEmptyFile(Path path) {
        try {
            return path != null && Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private Path tmpPath(Path target) {
        return target.resolveSibling(target.getFileName() + ".tmp");
    }

    private void moveReplace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Windows 上 ATOMIC_MOVE 可能不可用
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // ignore
        }
    }

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
            return String.valueOf(input.hashCode());
        }
    }
}
