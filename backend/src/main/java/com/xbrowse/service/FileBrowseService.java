package com.xbrowse.service;

import com.xbrowse.dto.FileItem;
import com.xbrowse.util.AlistClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 文件浏览核心服务
 */
@Service
public class FileBrowseService {

    private final AlistEngineService engineService;

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

    public FileBrowseService(AlistEngineService engineService) {
        this.engineService = engineService;
    }

    /**
     * 浏览目录
     */
    public List<FileItem> listFiles(Long engineId, String path, boolean refresh, int page, int perPage) {
        AlistClient client = engineService.getClient(engineId);
        path = normalizePath(path);
        List<FileItem> items = client.listFiles(path, refresh, page, perPage);
        for (FileItem item : items) {
            enrichFileItem(item, engineId);
        }
        return items;
    }

    /**
     * 获取文件预览 URL
     */
    public String getFilePreviewUrl(Long engineId, String filePath) {
        AlistClient client = engineService.getClient(engineId);
        return client.getFileUrl(filePath);
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

        if (isImageFile(fileName) || isVideoFile(fileName)) {
            item.setUrl("/api/files/proxy/" + engineId + "/" + encodePath(item.getPath()));
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
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 编码路径用于 URL
     */
    private String encodePath(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
