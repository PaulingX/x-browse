package com.xbrowse.service;

import com.xbrowse.config.AppConfig;
import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.BrowseDirectory;
import com.xbrowse.repository.BrowseDirectoryRepository;
import com.xbrowse.util.AlistClient;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.List;

/**
 * 本地缓存服务
 * 负责文件下载缓存和缩略图生成
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final BrowseDirectoryRepository directoryRepository;
    private final AlistEngineService engineService;
    private final FileBrowseService fileBrowseService;
    private final AppConfig appConfig;

    public CacheService(BrowseDirectoryRepository directoryRepository,
                        AlistEngineService engineService,
                        FileBrowseService fileBrowseService,
                        AppConfig appConfig) {
        this.directoryRepository = directoryRepository;
        this.engineService = engineService;
        this.fileBrowseService = fileBrowseService;
        this.appConfig = appConfig;
    }

    /**
     * 定时扫描启用缓存的目录，增量缓存新文件
     * 每 10 分钟执行一次
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 60000)
    public void scheduledScan() {
        log.info("开始定时扫描缓存目录...");
        List<BrowseDirectory> cacheDirs = directoryRepository.findByCacheEnabledTrue();

        for (BrowseDirectory dir : cacheDirs) {
            try {
                scanAndCacheDirectory(dir.getEngineId(), dir.getPath());
            } catch (Exception e) {
                log.error("扫描目录失败: engine={}, path={}", dir.getEngineId(), dir.getPath(), e);
            }
        }
        log.info("定时扫描缓存目录完成");
    }

    /**
     * 扫描并缓存目录中的新文件
     */
    public void scanAndCacheDirectory(Long engineId, String path) {
        log.info("扫描目录: engine={}, path={}", engineId, path);
        AlistClient client = engineService.getClient(engineId);

        List<FileItem> items = client.listFiles(path, false);
        if (items == null) return;

        for (FileItem item : items) {
            if (item.getIsDir()) {
                // 递归扫描子目录
                scanAndCacheDirectory(engineId, item.getPath());
            } else {
                // 检查是否为媒体文件
                if (fileBrowseService.isImageFile(item.getName()) ||
                    fileBrowseService.isVideoFile(item.getName())) {
                    // 检查本地是否已缓存
                    String localPath = fileBrowseService.getLocalCachePath(engineId, item.getPath());
                    if (localPath == null) {
                        // 下载并缓存文件
                        cacheFile(engineId, item);
                    }
                }
            }
        }
    }

    /**
     * 缓存单个文件
     */
    public boolean cacheFile(Long engineId, FileItem item) {
        try {
            log.info("缓存文件: engine={}, path={}", engineId, item.getPath());

            // 获取文件下载链接
            String downloadUrl = fileBrowseService.getFilePreviewUrl(engineId, item.getPath());
            if (downloadUrl == null) {
                log.warn("无法获取文件链接: {}", item.getPath());
                return false;
            }

            // 构建本地缓存路径
            String localPath = fileBrowseService.buildCachePath(engineId, item.getPath());
            Path localFilePath = Paths.get(localPath);

            // 创建目录
            Files.createDirectories(localFilePath.getParent());

            // 下载文件
            downloadFile(downloadUrl, localFilePath);

            // 如果是图片且启用缩略图，生成缩略图
            if (fileBrowseService.isImageFile(item.getName()) && appConfig.getThumbnailEnabled()) {
                generateThumbnail(localPath);
            }

            return true;
        } catch (Exception e) {
            log.error("缓存文件失败: {}", item.getPath(), e);
            return false;
        }
    }

    /**
     * 下载文件到本地
     */
    private void downloadFile(String url, Path targetPath) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(targetPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 生成缩略图
     */
    private void generateThumbnail(String imagePath) {
        try {
            int lastDot = imagePath.lastIndexOf('.');
            String thumbPath = lastDot > 0
                    ? imagePath.substring(0, lastDot) + "_thumb" + imagePath.substring(lastDot)
                    : imagePath + "_thumb";
            File thumbFile = new File(thumbPath);

            // 如果缩略图已存在则跳过
            if (thumbFile.exists()) {
                return;
            }

            Thumbnails.of(new File(imagePath))
                    .size(300, 300)
                    .keepAspectRatio(true)
                    .outputQuality(0.8)
                    .toFile(thumbFile);

            log.debug("生成缩略图: {}", thumbPath);
        } catch (Exception e) {
            log.warn("生成缩略图失败: {}", imagePath, e);
        }
    }

    /**
     * 获取缓存目录大小
     */
    public long getCacheSize() {
        File cacheDir = new File(appConfig.getCacheDir());
        if (!cacheDir.exists()) {
            return 0;
        }
        return calculateDirSize(cacheDir);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        File cacheDir = new File(appConfig.getCacheDir());
        if (cacheDir.exists()) {
            deleteDir(cacheDir);
        }
    }

    /**
     * 清理指定目录的缓存
     */
    public void clearDirectoryCache(Long engineId, String path) {
        String cachePath = fileBrowseService.buildCachePath(engineId, path);
        File cacheDir = new File(cachePath);
        if (cacheDir.exists()) {
            deleteDir(cacheDir);
            log.info("已清理目录缓存: engine={}, path={}", engineId, path);
        }
    }

    /**
     * 递归计算目录大小
     */
    private long calculateDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateDirSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    /**
     * 递归删除目录
     */
    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDir(file);
                }
            }
        }
        dir.delete();
    }
}
