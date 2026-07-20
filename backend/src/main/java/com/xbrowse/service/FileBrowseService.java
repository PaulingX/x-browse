package com.xbrowse.service;

import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.DirFile;
import com.xbrowse.entity.IndexedDirectory;
import com.xbrowse.repository.DirFileRepository;
import com.xbrowse.repository.IndexedDirectoryRepository;
import lombok.extern.slf4j.Slf4j;
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
    private final IndexedDirectoryRepository indexedDirectoryRepository;

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

    public FileBrowseService(DirFileRepository dirFileRepository,
                             IndexedDirectoryRepository indexedDirectoryRepository) {
        this.dirFileRepository = dirFileRepository;
        this.indexedDirectoryRepository = indexedDirectoryRepository;
    }

    /**
     * 浏览目录（从数据库读取）
     */
    public List<FileItem> listFiles(Long engineId, String path, boolean refresh, int page, int perPage, String sortMode) {
        path = normalizePath(path);
        log.debug("查询目录: engineId={}, path={}, page={}, perPage={}, sort={}", engineId, path, page, perPage, sortMode);

        Optional<IndexedDirectory> directoryOpt = indexedDirectoryRepository.findByEngineIdAndPath(engineId, path);
        if (directoryOpt.isPresent()) {
            IndexedDirectory directory = directoryOpt.get();
            List<FileItem> items = new ArrayList<>();
            indexedDirectoryRepository.findByEngineIdAndParentIdOrderByNameAsc(engineId, directory.getId()).stream()
                    .map(this::toDirectoryItem)
                    .forEach(items::add);
            dirFileRepository.findByDirectoryIdOrderByNameAsc(directory.getId()).stream()
                    .map(df -> toFileItem(df, engineId))
                    .forEach(items::add);
            items.sort(buildComparator(sortMode));
            List<FileItem> pageItems = pageItems(items, page, perPage);
            log.debug("查询结果: engineId={}, path={}, total={}", engineId, path, items.size());
            return pageItems;
        }

        List<FileItem> fallbackItems = dirFileRepository.findByEngineIdAndParentPathOrderByIsDirDescNameAsc(engineId, path)
                .stream()
                .map(df -> toFileItem(df, engineId))
                .sorted(buildComparator(sortMode))
                .collect(Collectors.toList());
        log.debug("查询结果(兼容旧数据): engineId={}, path={}, total={}", engineId, path, fallbackItems.size());
        return pageItems(fallbackItems, page, perPage);
    }

    public List<FileItem> searchFiles(Long engineId, String parentPath, String keyword) {
        String path = normalizePath(parentPath);
        String lowerKeyword = keyword == null ? "" : keyword.toLowerCase();
        Optional<IndexedDirectory> directoryOpt = indexedDirectoryRepository.findByEngineIdAndPath(engineId, path);
        if (directoryOpt.isPresent()) {
            IndexedDirectory directory = directoryOpt.get();
            List<FileItem> items = new ArrayList<>();
            indexedDirectoryRepository.findByEngineIdAndParentIdOrderByNameAsc(engineId, directory.getId()).stream()
                    .filter(dir -> dir.getName() != null && dir.getName().toLowerCase().contains(lowerKeyword))
                    .map(this::toDirectoryItem)
                    .forEach(items::add);
            dirFileRepository.searchByNameAndDirectoryId(directory.getId(), keyword).stream()
                    .map(df -> toFileItem(df, engineId))
                    .forEach(items::add);
            items.sort(buildComparator("name_asc"));
            return items;
        }

        return dirFileRepository.searchByNameAndParentPath(engineId, path, keyword).stream()
                .map(df -> toFileItem(df, engineId))
                .sorted(buildComparator("name_asc"))
                .collect(Collectors.toList());
    }

    private Comparator<FileItem> buildComparator(String sortMode) {
        String mode = sortMode == null ? "name_asc" : sortMode;
        boolean desc = mode.endsWith("_desc");

        Comparator<FileItem> dirFirst = Comparator.comparing(item -> !Boolean.TRUE.equals(item.getIsDir()));
        Comparator<FileItem> primary;
        if (mode.startsWith("time_")) {
            primary = (left, right) -> compareNullableLong(left.getModified(), right.getModified(), desc);
        } else {
            primary = Comparator.comparing(item -> item.getName() == null ? "" : item.getName().toLowerCase());
            if (desc) {
                primary = primary.reversed();
            }
        }
        Comparator<FileItem> nameAsc = Comparator.comparing(item -> item.getName() == null ? "" : item.getName().toLowerCase());
        return dirFirst.thenComparing(primary).thenComparing(nameAsc);
    }

    private int compareNullableLong(Long left, Long right, boolean desc) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        int compared = left.compareTo(right);
        return desc ? -compared : compared;
    }

    private List<FileItem> pageItems(List<FileItem> items, int page, int perPage) {
        int safePage = Math.max(page, 1);
        int safePerPage = Math.max(perPage, 1);
        int start = (safePage - 1) * safePerPage;
        if (start >= items.size()) {
            return List.of();
        }
        int end = Math.min(start + safePerPage, items.size());
        return items.subList(start, end);
    }

    /**
     * 获取目录预览图（优先使用数据库中缓存的预览图 URL）
     */
    public String getDirThumbnail(Long engineId, String dirPath) {
        dirPath = normalizePath(dirPath);
        log.debug("获取目录预览图: engineId={}, dirPath={}", engineId, dirPath);

        Optional<IndexedDirectory> directoryOpt = indexedDirectoryRepository.findByEngineIdAndPath(engineId, dirPath);
        if (directoryOpt.isPresent()) {
            IndexedDirectory directory = directoryOpt.get();
            if (directory.getThumbnailUrl() != null) {
                log.debug("使用缓存的预览图URL: engineId={}, dirPath={}, url={}", engineId, dirPath, directory.getThumbnailUrl());
                return directory.getThumbnailUrl();
            }

            List<DirFile> files = dirFileRepository.findByDirectoryIdOrderByNameAsc(directory.getId());
            for (DirFile df : files) {
                if (isImageFile(df.getName())) {
                    String fullPath = buildChildPath(df.getParentPath(), df.getName());
                    return "/api/files/proxy/" + engineId + "/" + encodePath(fullPath);
                }
            }

            List<IndexedDirectory> childDirectories = indexedDirectoryRepository.findByEngineIdAndParentIdOrderByNameAsc(engineId, directory.getId());
            for (IndexedDirectory child : childDirectories) {
                if (child.getThumbnailUrl() != null) {
                    return child.getThumbnailUrl();
                }
            }
            return null;
        }

        // 兼容升级前仅有 parent_path 的旧数据
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
                String fullPath = buildChildPath(df.getParentPath(), df.getName());
                return "/api/files/proxy/" + engineId + "/" + encodePath(fullPath);
            }
        }

        if (firstSubDir != null) {
            List<DirFile> subItems = dirFileRepository.findByEngineIdAndParentPathOrderByIsDirDescNameAsc(engineId, firstSubDir);
            for (DirFile df : subItems) {
                if (!df.getIsDir() && isImageFile(df.getName())) {
                    String fullPath = buildChildPath(df.getParentPath(), df.getName());
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

        String fullPath = buildChildPath(df.getParentPath(), df.getName());
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

    private FileItem toDirectoryItem(IndexedDirectory directory) {
        FileItem fi = new FileItem();
        fi.setName(directory.getName());
        fi.setIsDir(true);
        fi.setPath(directory.getPath());
        fi.setUrl(directory.getThumbnailUrl());
        fi.setModified(directory.getModifiedTime());
        return fi;
    }

    private String buildChildPath(String parentPath, String name) {
        return parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;
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
