package com.xbrowse.service;

import com.xbrowse.util.AlistClient;
import com.xbrowse.util.MediaTypes;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 列表缩略图本地缓存服务
 * <p>
 * 规则：原图 <= 阈值（默认 500KB）不生成缩略图，列表直接用原图；
 * 超过阈值则原图转 WebP（不缩放，quality=1）写入缓存。
 */
@Slf4j
@Service
public class ThumbnailCacheService {

    private static final String THUMBNAILS_DIR = "thumbnails";
    private static final String THUMB_EXT = ".webp";
    private static final float WEBP_QUALITY = 1.0f;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60_000;
    private static final int MAX_CACHE_WRITERS = 2;
    private static final long LOCK_WAIT_MS = 30_000L;
    private static final int STRIPE_COUNT = 64;

    @Value("${xbrowse.cache-dir:./data/cache}")
    private String cacheDir;

    @Value("${xbrowse.thumbnail-enabled:true}")
    private boolean thumbnailEnabled;

    /** 小于等于该字节数不生成缩略图（默认 500KB） */
    @Value("${xbrowse.thumbnail-min-source-bytes:512000}")
    private long minSourceBytes;

    private final Semaphore writeLimiter = new Semaphore(MAX_CACHE_WRITERS, true);
    private final Object[] fileStripes = new Object[STRIPE_COUNT];

    public ThumbnailCacheService() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            fileStripes[i] = new Object();
        }
    }

    @PostConstruct
    public void init() {
        ImageIO.scanForPlugins();
        try {
            Files.createDirectories(Paths.get(cacheDir, THUMBNAILS_DIR));
            log.info("缩略图缓存目录初始化完成: {}, minSourceBytes={}, webpQuality={}",
                    Paths.get(cacheDir, THUMBNAILS_DIR).toAbsolutePath(), minSourceBytes, WEBP_QUALITY);
            boolean hasWebp = false;
            for (String f : ImageIO.getWriterFormatNames()) {
                if ("webp".equalsIgnoreCase(f)) {
                    hasWebp = true;
                    break;
                }
            }
            if (!hasWebp) {
                log.warn("ImageIO 未注册 WebP 写出器，请确认 webp-imageio 依赖已打包");
            }
        } catch (IOException e) {
            log.error("创建缩略图缓存目录失败: {}", cacheDir, e);
        }
    }

    /**
     * 缓存列表缩略图。
     *
     * @return 缩略图 URL；小图或不生成时返回 null（调用方回退原图代理）
     */
    public String cacheThumbnail(Long engineId, String imagePath, AlistClient client) {
        if (!thumbnailEnabled || engineId == null || imagePath == null) {
            return null;
        }
        String cacheFileName = md5(imagePath) + THUMB_EXT;
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
                Boolean result = downloadAndConvertWebp(fileUrl, cachePath);
                if (result == null) {
                    // 小图：不生成缩略图
                    return null;
                }
                if (result && isNonEmptyFile(cachePath)) {
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

    private Object stripeLock(Path path) {
        int idx = Math.floorMod(path.toString().hashCode(), STRIPE_COUNT);
        return fileStripes[idx];
    }

    private String buildThumbnailUrl(Long engineId, String cacheFileName) {
        return "/api/files/thumbnail/" + engineId + "/" + cacheFileName;
    }

    /**
     * @return true 已生成缩略图；false 失败；null 源图过小跳过
     */
    private Boolean downloadAndConvertWebp(String fileUrl, Path targetPath) throws IOException {
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
            if (data.length <= minSourceBytes) {
                log.debug("源图 <= {}B，跳过缩略图: size={}B", minSourceBytes, data.length);
                return null;
            }
            if (!writeOriginalAsWebp(data, tmp)) {
                return false;
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

    /** 原图转 WebP（不缩放），quality=1 */
    private boolean writeOriginalAsWebp(byte[] data, Path tmp) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) {
                log.warn("无法解码图片生成 WebP 缩略图, size={}B", data.length);
                return false;
            }
            BufferedImage rgb = toRgb(src);
            if (!writeWebp(rgb, tmp, WEBP_QUALITY)) {
                log.warn("写出 WebP 缩略图失败");
                deleteQuietly(tmp);
                return false;
            }
            return isNonEmptyFile(tmp);
        } catch (Exception e) {
            log.warn("生成 WebP 缩略图失败: {}", e.toString());
            deleteQuietly(tmp);
            return false;
        }
    }

    private boolean writeWebp(BufferedImage image, Path path, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            return ImageIO.write(image, "webp", path.toFile());
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0) {
                    String chosen = types[0];
                    for (String t : types) {
                        if (t != null && t.toLowerCase().contains("loss")) {
                            chosen = t;
                            break;
                        }
                    }
                    param.setCompressionType(chosen);
                }
                try {
                    param.setCompressionQuality(quality);
                } catch (UnsupportedOperationException ignored) {
                    // ignore
                }
            }
            writer.write(null, new IIOImage(image, null, null), param);
            return true;
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
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