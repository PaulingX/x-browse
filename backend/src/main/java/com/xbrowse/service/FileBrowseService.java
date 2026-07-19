package com.xbrowse.service;

import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.DirFile;
import com.xbrowse.repository.DirFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件浏览核心服务
 */
@Slf4j
@Service
public class FileBrowseService {

    private final DirFileRepository dirFileRepository;

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

    public FileBrowseService(DirFileRepository dirFileRepository) {
        this.dirFileRepository = dirFileRepository;
    }

    /**
     * 浏览目录（从数据库读取）
     */
    public List<FileItem> listFiles(Long engineId, String path, boolean refresh, int page, int perPage) {
        path = normalizePath(path);
        log.debug("查询目录: engineId={}, path={}, page={}, perPage={}", engineId, path, page, perPage);
        Page<DirFile> pageData = dirFileRepository
                .findByEngineIdAndParentPathOrderByIsDirDescNameAsc(engineId, path, PageRequest.of(page - 1, perPage));
        log.debug("查询结果: engineId={}, path={}, total={}", engineId, path, pageData.getTotalElements());
        return pageData.getContent().stream()
                .map(df -> toFileItem(df, engineId))
                .collect(Collectors.toList());
    }

    /**
     * 获取目录预览图（优先使用数据库中缓存的预览图 URL）
     */
    public String getDirThumbnail(Long engineId, String dirPath) {
        dirPath = normalizePath(dirPath);
        log.debug("获取目录预览图: engineId={}, dirPath={}", engineId, dirPath);

        // 直接从数据库读取该目录的 thumbnailUrl（同步时已缓存）
        DirFile dirFile = dirFileRepository.findByEngineIdAndParentPathAndName(engineId, parentOf(dirPath), nameOf(dirPath));
        if (dirFile != null && dirFile.getThumbnailUrl() != null) {
            log.debug("使用缓存的预览图URL: engineId={}, dirPath={}, url={}", engineId, dirPath, dirFile.getThumbnailUrl());
            return dirFile.getThumbnailUrl();
        }

        // 数据库中没有预览图，回退到扫描子项
        log.debug("数据库中无预览图，扫描子项: engineId={}, dirPath={}", engineId, dirPath);
        List<DirFile> items = dirFileRepository.findByEngineIdAndParentPathOrderByIsDirDescNameAsc(engineId, dirPath);

        String firstSubDir = null;
        for (DirFile df : items) {
            if (df.getIsDir()) {
                if (firstSubDir == null) {
                    firstSubDir = df.getParentPath().endsWith("/")
                            ? df.getParentPath() + df.getName()
                            : df.getParentPath() + "/" + df.getName();
                }
                continue;
            }
            if (isImageFile(df.getName())) {
                String fullPath = df.getParentPath().endsWith("/")
                        ? df.getParentPath() + df.getName()
                        : df.getParentPath() + "/" + df.getName();
                return "/api/files/proxy/" + engineId + "/" + encodePath(fullPath);
            }
        }

        if (firstSubDir != null) {
            List<DirFile> subItems = dirFileRepository.findByEngineIdAndParentPathOrderByIsDirDescNameAsc(engineId, firstSubDir);
            for (DirFile df : subItems) {
                if (!df.getIsDir() && isImageFile(df.getName())) {
                    String fullPath = df.getParentPath().endsWith("/")
                            ? df.getParentPath() + df.getName()
                            : df.getParentPath() + "/" + df.getName();
                    return "/api/files/proxy/" + engineId + "/" + encodePath(fullPath);
                }
            }
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
     * DirFile 实体转 FileItem DTO
     */
    private FileItem toFileItem(DirFile df, Long engineId) {
        FileItem fi = new FileItem();
        fi.setName(df.getName());
        fi.setIsDir(df.getIsDir());
        fi.setSize(df.getSize());
        fi.setExt(df.getExt());

        String fullPath = df.getParentPath().endsWith("/")
                ? df.getParentPath() + df.getName()
                : df.getParentPath() + "/" + df.getName();
        fi.setPath(fullPath);
        if (df.getModifiedTime() != null) {
            fi.setModified(df.getModifiedTime());
        }

        if (df.getIsDir()) {
            // 目录项：使用数据库中存储的预览图 URL（可能是本地缓存 URL 或代理 URL）
            fi.setUrl(df.getThumbnailUrl());
        } else {
            if (isVideoFile(df.getName())) {
                fi.setUrl("/api/files/stream/" + engineId + "/" + encodePath(fullPath));
            } else if (isImageFile(df.getName())) {
                fi.setUrl("/api/files/proxy/" + engineId + "/" + encodePath(fullPath));
            }
        }

        return fi;
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
     * 获取父目录路径
     */
    private String parentOf(String path) {
        if (path.equals("/")) return "/";
        String p = path.substring(0, path.lastIndexOf('/'));
        return p.isEmpty() ? "/" : p;
    }

    /**
     * 获取路径最后一段名称
     */
    private String nameOf(String path) {
        if (path.equals("/")) return "/";
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 编码路径用于 URL
     */
    private String encodePath(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = p.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
