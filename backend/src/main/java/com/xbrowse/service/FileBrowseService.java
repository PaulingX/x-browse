package com.xbrowse.service;

import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.DirFile;
import com.xbrowse.entity.FileDirectory;
import com.xbrowse.repository.DirFileRepository;
import com.xbrowse.repository.FileDirectoryRepository;
import com.xbrowse.util.AlistClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 文件浏览核心服务
 */
@Slf4j
@Service
public class FileBrowseService {

    private final DirFileRepository dirFileRepository;
    private final FileDirectoryRepository fileDirectoryRepository;
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

    public FileBrowseService(DirFileRepository dirFileRepository, FileDirectoryRepository fileDirectoryRepository,
                             AlistEngineService engineService) {
        this.dirFileRepository = dirFileRepository;
        this.fileDirectoryRepository = fileDirectoryRepository;
        this.engineService = engineService;
    }

    /**
     * 浏览目录（优先数据库；refresh 或库中无数据时直连 Alist）
     */
    public List<FileItem> listFiles(Long engineId, String path, boolean refresh, int page, int perPage, String sortMode) {
        path = normalizePath(path);
        page = Math.max(page, 1);
        perPage = Math.max(perPage, 1);
        log.debug("查询目录: engineId={}, path={}, refresh={}, page={}, perPage={}, sort={}", engineId, path, refresh, page, perPage, sortMode);

        if (!refresh) {
            Optional<FileDirectory> directoryOpt = fileDirectoryRepository.findByEngineIdAndPath(engineId, path);
            if (directoryOpt.isPresent()) {
                FileDirectory directory = directoryOpt.get();
                List<FileItem> items = new ArrayList<>();
                items.addAll(fileDirectoryRepository.findByEngineIdAndParentId(engineId, directory.getId()).stream()
                        .map(dir -> toFileItem(dir, engineId))
                        .toList());
                items.addAll(dirFileRepository.findByDirectoryId(directory.getId()).stream()
                        .map(df -> toFileItem(df, engineId))
                        .toList());
                return paginate(sortItems(items, sortMode), page, perPage);
            }
        }

        return paginate(sortItems(listFromAlist(engineId, path, refresh), sortMode), page, perPage);
    }

    private List<FileItem> listFromAlist(Long engineId, String path, boolean refresh) {
        try {
            AlistClient client = engineService.getClient(engineId);
            List<FileItem> items = client.listFiles(path, refresh, 1, 1000);
            for (FileItem item : items) {
                if (item.getIsDir() == null || !item.getIsDir()) {
                    if (isVideoFile(item.getName())) {
                        item.setUrl("/api/files/stream/" + engineId + "/" + encodePath(item.getPath()));
                    } else if (isImageFile(item.getName())) {
                        item.setUrl("/api/files/proxy/" + engineId + "/" + encodePath(item.getPath()));
                    }
                }
            }
            return items;
        } catch (Exception e) {
            log.error("直连 Alist 获取目录失败: engineId={}, path={}", engineId, path, e);
            return List.of();
        }
    }

    private List<FileItem> sortItems(List<FileItem> items, String sortMode) {
        List<FileItem> sorted = new ArrayList<>(items);
        sorted.sort(buildComparator(sortMode));
        return sorted;
    }

    private List<FileItem> paginate(List<FileItem> items, int page, int perPage) {
        int fromIndex = Math.max(0, (page - 1) * perPage);
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(items.size(), fromIndex + perPage);
        return items.subList(fromIndex, toIndex);
    }

    public List<FileItem> searchFiles(Long engineId, String parentPath, String keyword) {
        parentPath = normalizePath(parentPath);
        Optional<FileDirectory> directoryOpt = fileDirectoryRepository.findByEngineIdAndPath(engineId, parentPath);
        if (directoryOpt.isEmpty()) {
            return List.of();
        }

        Long directoryId = directoryOpt.get().getId();
        List<FileItem> items = new ArrayList<>();
        items.addAll(fileDirectoryRepository.searchByNameAndParentId(engineId, directoryId, keyword).stream()
                .map(dir -> toFileItem(dir, engineId))
                .toList());
        items.addAll(dirFileRepository.searchByNameAndDirectoryId(directoryId, keyword).stream()
                .map(df -> toFileItem(df, engineId))
                .toList());
        items.sort(buildComparator("name_asc"));
        return items;
    }

    private Comparator<FileItem> buildComparator(String sortMode) {
        String mode = sortMode == null ? "name_asc" : sortMode;
        Comparator<FileItem> primary = mode.startsWith("time_")
                ? Comparator.comparing(item -> item.getModified() == null ? 0L : item.getModified())
                : Comparator.comparing(item -> item.getName() == null ? "" : item.getName().toLowerCase());
        if (mode.endsWith("_desc")) {
            primary = primary.reversed();
        }
        return Comparator.comparing(FileItem::getIsDir, Comparator.nullsLast(Boolean::compareTo)).reversed()
                .thenComparing(primary)
                .thenComparing(item -> item.getName() == null ? "" : item.getName().toLowerCase());
    }

    /**
     * 获取目录预览图（优先使用数据库中缓存的预览图 URL）
     */
    public String getDirThumbnail(Long engineId, String dirPath) {
        dirPath = normalizePath(dirPath);
        log.debug("获取目录预览图: engineId={}, dirPath={}", engineId, dirPath);

        return fileDirectoryRepository.findByEngineIdAndPath(engineId, dirPath)
                .map(FileDirectory::getThumbnailUrl)
                .orElse(null);
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

    private FileItem toFileItem(FileDirectory directory, Long engineId) {
        FileItem fi = new FileItem();
        fi.setName(directory.getName());
        fi.setIsDir(true);
        fi.setSize(0L);
        fi.setPath(directory.getPath());
        fi.setModified(directory.getModifiedTime());
        fi.setUrl(directory.getThumbnailUrl());
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
