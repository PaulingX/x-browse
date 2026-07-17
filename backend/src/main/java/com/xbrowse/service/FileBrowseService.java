package com.xbrowse.service;

import com.xbrowse.config.AppConfig;
import com.xbrowse.dto.FileItem;
import com.xbrowse.util.AlistClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件浏览核心服务
 */
@Service
public class FileBrowseService {

    private final AlistEngineService engineService;
    private final AppConfig appConfig;

    /**
     * 图片扩展名
     */
    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"
    );

    /**
     * 视频扩展名
     */
    private static final Set<String> VIDEO_EXTS = Set.of(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v"
    );

    public FileBrowseService(AlistEngineService engineService, AppConfig appConfig) {
        this.engineService = engineService;
        this.appConfig = appConfig;
    }

    /**
     * 浏览目录
     *
     * @param engineId 引擎 ID
     * @param path     目录路径
     * @param refresh  是否刷新缓存
     * @return 文件列表
     */
    public List<FileItem> listFiles(Long engineId, String path, boolean refresh) {
        AlistClient client = engineService.getClient(engineId);

        // 标准化路径
        path = normalizePath(path);

        // 获取文件列表
        List<FileItem> items = client.listFiles(path, refresh);

        // 填充缩略图和预览 URL
        for (FileItem item : items) {
            enrichFileItem(item, engineId);
        }

        return items;
    }

    /**
     * 获取文件预览 URL
     *
     * @param engineId 引擎 ID
     * @param filePath 文件路径
     * @return 预览 URL
     */
    public String getFilePreviewUrl(Long engineId, String filePath) {
        AlistClient client = engineService.getClient(engineId);
        return client.getFileUrl(filePath);
    }

    /**
     * 获取本地缓存文件路径
     *
     * @param engineId 引擎 ID
     * @param filePath Alist 文件路径
     * @return 本地缓存文件路径，如果不存在返回 null
     */
    public String getLocalCachePath(Long engineId, String filePath) {
        String cachePath = buildCachePath(engineId, filePath);
        File cacheFile = new File(cachePath);
        if (cacheFile.exists()) {
            return cachePath;
        }
        return null;
    }

    /**
     * 判断是否为图片文件
     */
    public boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return false;
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        return IMAGE_EXTS.contains(ext);
    }

    /**
     * 判断是否为视频文件
     */
    public boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return false;
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        return VIDEO_EXTS.contains(ext);
    }

    /**
     * 填充文件项的额外信息（缩略图、预览 URL）
     */
    private void enrichFileItem(FileItem item, Long engineId) {
        if (item.getIsDir()) {
            return;
        }

        String fileName = item.getName();

        // 检查本地缓存
        String localCachePath = getLocalCachePath(engineId, item.getPath());

        if (isImageFile(fileName)) {
            // 图片文件
            if (localCachePath != null) {
                item.setThumbnail("/api/files/cache/thumb/" + engineId + encodePath(item.getPath()));
                item.setUrl("/api/files/cache/file/" + engineId + encodePath(item.getPath()));
            } else {
                item.setThumbnail("/api/files/proxy/thumb/" + engineId + encodePath(item.getPath()));
                item.setUrl("/api/files/proxy/file/" + engineId + encodePath(item.getPath()));
            }
        } else if (isVideoFile(fileName)) {
            // 视频文件
            if (localCachePath != null) {
                item.setThumbnail("/api/files/cache/thumb/" + engineId + encodePath(item.getPath()));
                item.setUrl("/api/files/stream/" + engineId + encodePath(item.getPath()));
            } else {
                item.setThumbnail("/api/files/proxy/thumb/" + engineId + encodePath(item.getPath()));
                item.setUrl("/api/files/stream/" + engineId + encodePath(item.getPath()));
            }
        }
    }

    /**
     * 标准化路径
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // 移除末尾的斜杠（根目录除外）
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 编码路径用于 URL
     */
    private String encodePath(String path) {
        return path.replace("/", "~");
    }

    /**
     * 构建本地缓存路径
     */
    public String buildCachePath(Long engineId, String filePath) {
        Path cacheDir = Paths.get(appConfig.getCacheDir(), String.valueOf(engineId));
        // 将路径中的 / 替换为 File.separator
        String relativePath = filePath.replace("/", File.separator);
        if (relativePath.startsWith(File.separator)) {
            relativePath = relativePath.substring(1);
        }
        return cacheDir.resolve(relativePath).toString();
    }

    /**
     * 构建缩略图缓存路径
     */
    public String buildThumbnailPath(Long engineId, String filePath) {
        String cachePath = buildCachePath(engineId, filePath);
        int lastDotIndex = cachePath.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return cachePath.substring(0, lastDotIndex) + "_thumb" + cachePath.substring(lastDotIndex);
        }
        return cachePath + "_thumb";
    }
}
