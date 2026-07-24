package com.xbrowse.service;

import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.DirFile;
import com.xbrowse.entity.FileDirectory;
import com.xbrowse.repository.DirFileRepository;
import com.xbrowse.repository.FileDirectoryRepository;
import com.xbrowse.util.AlistClient;
import com.xbrowse.util.MediaTypes;
import com.xbrowse.util.PathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 文件浏览核心服务
 * <p>
 * 本地库使用 SQL LIMIT/OFFSET 分页；视频封面优先读入库 coverUrl。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileBrowseService {

    private static final List<String> IMAGE_EXTS = List.copyOf(MediaTypes.IMAGE_EXTS);
    private static final List<String> VIDEO_EXTS = List.copyOf(MediaTypes.VIDEO_EXTS);

    private final DirFileRepository dirFileRepository;
    private final FileDirectoryRepository fileDirectoryRepository;
    private final AlistEngineService engineService;
    private final PathAccessService pathAccessService;
    private final MediaAccessService mediaAccessService;

    public List<FileItem> listFiles(Long engineId, String path, boolean refresh, int page, int perPage,
                                    String sortMode, String mediaType) {
        path = PathUtils.normalize(path);
        pathAccessService.assertPathAllowed(engineId, path);
        page = Math.max(page, 1);
        perPage = Math.min(Math.max(perPage, 1), 200);
        String sort = normalizeSort(sortMode);

        if (!refresh) {
            Optional<FileDirectory> directoryOpt = fileDirectoryRepository.findByEngineIdAndPath(engineId, path);
            if (directoryOpt.isPresent()) {
                List<FileItem> paged = listFromDbPaged(engineId, directoryOpt.get(), page, perPage, sort, mediaType);
                // file_directory 有记录但 directory_id 未回填时可能为空，再走 parent_path 兼容
                if (!paged.isEmpty() || dirFileRepository.countByDirectoryId(directoryOpt.get().getId()) > 0
                        || fileDirectoryRepository.countByEngineIdAndParentId(engineId, directoryOpt.get().getId()) > 0) {
                    return paged;
                }
            }
            // 旧数据：仅 dir_file + parent_path（directory_id 为空 / file_directory 未建）
            if (dirFileRepository.countByEngineIdAndParentPath(engineId, path) > 0) {
                return listFromParentPathPaged(engineId, path, page, perPage, sort, mediaType);
            }
        }

        List<FileItem> items = listFromAlist(engineId, path, refresh);
        applyVideoCoverInMemory(items);
        items = filterByMediaType(items, mediaType);
        return paginate(sortItems(items, sort), page, perPage);
    }

    public List<FileItem> searchFiles(Long engineId, String parentPath, String keyword, String mediaType) {
        parentPath = PathUtils.normalize(parentPath);
        pathAccessService.assertPathAllowed(engineId, parentPath);
        List<FileItem> items = new ArrayList<>();
        Optional<FileDirectory> directoryOpt = fileDirectoryRepository.findByEngineIdAndPath(engineId, parentPath);
        if (directoryOpt.isPresent()) {
            Long directoryId = directoryOpt.get().getId();
            items.addAll(fileDirectoryRepository.searchByNameAndParentId(engineId, directoryId, keyword).stream()
                    .map(this::toFileItem)
                    .toList());
            items.addAll(dirFileRepository.searchByNameAndDirectoryId(directoryId, keyword).stream()
                    .map(df -> toFileItem(df, engineId))
                    .toList());
        }
        // 兼容旧数据
        if (items.isEmpty()) {
            items.addAll(dirFileRepository.searchByNameAndParentPath(engineId, parentPath, keyword).stream()
                    .map(df -> toFileItem(df, engineId))
                    .toList());
        }
        return sortItems(filterByMediaType(items, mediaType), "name_asc");
    }

    public String getDirThumbnail(Long engineId, String dirPath) {
        String path = PathUtils.normalize(dirPath);
        if (!pathAccessService.isPathAllowed(engineId, path)) {
            return null;
        }
        Optional<FileDirectory> opt = fileDirectoryRepository.findByEngineIdAndPath(engineId, path);
        if (opt.isPresent()) {
            FileDirectory directory = opt.get();
            if (directory.getThumbnailUrl() != null && !directory.getThumbnailUrl().isEmpty()) {
                return mediaAccessService.withAccessToken(directory.getThumbnailUrl());
            }
            String fromChildren = resolveDirThumbFromChildren(engineId, directory.getId());
            if (fromChildren != null) {
                return mediaAccessService.withAccessToken(fromChildren);
            }
        }
        // 兼容旧数据：从 parent_path 子文件取首张图
        return mediaAccessService.withAccessToken(resolveDirThumbFromParentPath(engineId, path));
    }

    public boolean isImageFile(String fileName) {
        return MediaTypes.isImage(fileName);
    }

    public boolean isVideoFile(String fileName) {
        return MediaTypes.isVideo(fileName);
    }

    /**
     * SQL 分页：目录在前 + 文件在后（新模型：file_directory + dir_file.directory_id）
     */
    private List<FileItem> listFromDbPaged(Long engineId, FileDirectory directory,
                                           int page, int perPage, String sort, String mediaType) {
        Long dirId = directory.getId();
        long dirCount = fileDirectoryRepository.countByEngineIdAndParentId(engineId, dirId);
        int offset = (page - 1) * perPage;
        List<FileItem> result = new ArrayList<>(perPage);

        if (offset < dirCount) {
            int dirSkip = offset;
            int dirTake = (int) Math.min(perPage, dirCount - dirSkip);
            List<FileDirectory> dirs = pageChildDirs(engineId, dirId, sort, dirSkip, dirTake);
            for (FileDirectory d : dirs) {
                result.add(toFileItem(d));
            }
        }

        int remaining = perPage - result.size();
        if (remaining > 0) {
            int fileOffset = (int) Math.max(0, offset - dirCount);
            List<DirFile> files = pageFiles(dirId, sort, mediaType, fileOffset, remaining);
            for (DirFile df : files) {
                result.add(toFileItem(df, engineId));
            }
        }
        return result;
    }

    /**
     * SQL 分页：兼容旧数据（dir_file 按 parent_path，is_dir 区分目录/文件）
     */
    private List<FileItem> listFromParentPathPaged(Long engineId, String path,
                                                   int page, int perPage, String sort, String mediaType) {
        long dirCount = dirFileRepository.countDirsByEngineIdAndParentPath(engineId, path);
        int offset = (page - 1) * perPage;
        List<FileItem> result = new ArrayList<>(perPage);

        if (offset < dirCount) {
            int dirSkip = offset;
            int dirTake = (int) Math.min(perPage, dirCount - dirSkip);
            List<DirFile> dirs = dirFileRepository.pageDirsByParentPath(engineId, path, sort, dirTake, dirSkip);
            for (DirFile df : dirs) {
                result.add(toFileItem(df, engineId));
            }
        }

        int remaining = perPage - result.size();
        if (remaining > 0) {
            int fileOffset = (int) Math.max(0, offset - dirCount);
            List<DirFile> files = pageFilesByParentPath(engineId, path, sort, mediaType, fileOffset, remaining);
            for (DirFile df : files) {
                result.add(toFileItem(df, engineId));
            }
        }
        return result;
    }

    private List<DirFile> pageFilesByParentPath(Long engineId, String parentPath, String sort,
                                                String mediaType, int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String type = mediaType == null ? "all" : mediaType.trim().toLowerCase(Locale.ROOT);
        if ("image".equals(type)) {
            return dirFileRepository.pageFilesByParentPathAndExtIn(engineId, parentPath, IMAGE_EXTS, sort, limit, offset);
        }
        if ("video".equals(type)) {
            return dirFileRepository.pageFilesByParentPathAndExtIn(engineId, parentPath, VIDEO_EXTS, sort, limit, offset);
        }
        return dirFileRepository.pageFilesByParentPath(engineId, parentPath, sort, limit, offset);
    }

    private List<FileDirectory> pageChildDirs(Long engineId, Long parentId, String sort, int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        if (parentId == null) {
            return fileDirectoryRepository.pageRootChildren(engineId, sort, limit, offset);
        }
        return fileDirectoryRepository.pageChildren(engineId, parentId, sort, limit, offset);
    }

    private List<DirFile> pageFiles(Long directoryId, String sort, String mediaType, int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String type = mediaType == null ? "all" : mediaType.trim().toLowerCase(Locale.ROOT);
        if ("image".equals(type)) {
            return dirFileRepository.pageByDirectoryIdAndExtIn(directoryId, IMAGE_EXTS, sort, limit, offset);
        }
        if ("video".equals(type)) {
            return dirFileRepository.pageByDirectoryIdAndExtIn(directoryId, VIDEO_EXTS, sort, limit, offset);
        }
        return dirFileRepository.pageByDirectoryId(directoryId, sort, limit, offset);
    }

    private String normalizeSort(String sortMode) {
        if (sortMode == null || sortMode.isBlank()) {
            return "name_asc";
        }
        String s = sortMode.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "name_desc", "time_asc", "time_desc" -> s;
            default -> "name_asc";
        };
    }

    private List<FileItem> filterByMediaType(List<FileItem> items, String mediaType) {
        if (items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }
        String type = mediaType == null ? "all" : mediaType.trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty() || "all".equals(type) || "mixed".equals(type)) {
            return items;
        }
        List<FileItem> filtered = new ArrayList<>(items.size());
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir())) {
                filtered.add(item);
                continue;
            }
            String name = item.getName();
            if ("image".equals(type) && MediaTypes.isImage(name)) {
                filtered.add(item);
            } else if ("video".equals(type) && MediaTypes.isVideo(name)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    /** Alist 直连回退时的内存封面关联 */
    private void applyVideoCoverInMemory(List<FileItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, FileItem> coverByBase = new HashMap<>();
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir()) || item.getName() == null || !MediaTypes.isImage(item.getName())) {
                continue;
            }
            String base = baseNameWithoutExt(item.getName()).toLowerCase(Locale.ROOT);
            FileItem existing = coverByBase.get(base);
            if (existing == null || coverImagePriority(item.getName()) < coverImagePriority(existing.getName())) {
                coverByBase.put(base, item);
            }
        }
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir()) || item.getName() == null || !MediaTypes.isVideo(item.getName())) {
                continue;
            }
            FileItem cover = coverByBase.get(baseNameWithoutExt(item.getName()).toLowerCase(Locale.ROOT));
            if (cover == null) {
                continue;
            }
            String thumb = cover.getThumbnailUrl();
            if (thumb == null || thumb.isEmpty()) {
                thumb = cover.getUrl();
            }
            item.setThumbnailUrl(thumb);
        }
    }

    private static String baseNameWithoutExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static int coverImagePriority(String fileName) {
        String ext = MediaTypes.extensionOf(fileName);
        if (ext == null) {
            return 100;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> 1;
            case "png" -> 2;
            case "webp" -> 3;
            case "gif" -> 4;
            case "bmp" -> 5;
            default -> 50;
        };
    }

    private String resolveDirThumbFromChildren(Long engineId, Long directoryId) {
        if (directoryId == null) {
            return null;
        }
        List<DirFile> files = dirFileRepository.findImageFilesByDirectoryId(directoryId, IMAGE_EXTS);
        for (DirFile df : files) {
            if (df.getName() == null) {
                continue;
            }
            return MediaTypes.proxyUrl(engineId, PathUtils.join(df.getParentPath(), df.getName()));
        }
        List<FileDirectory> subDirs = fileDirectoryRepository.findByEngineIdAndParentId(engineId, directoryId);
        for (FileDirectory sub : subDirs) {
            if (sub.getThumbnailUrl() != null && !sub.getThumbnailUrl().isEmpty()) {
                return sub.getThumbnailUrl();
            }
        }
        return null;
    }

    private String resolveDirThumbFromParentPath(Long engineId, String dirPath) {
        List<DirFile> files = dirFileRepository.findImageFilesByParentPath(engineId, dirPath, IMAGE_EXTS);
        for (DirFile df : files) {
            if (df.getName() == null) {
                continue;
            }
            return MediaTypes.proxyUrl(engineId, PathUtils.join(df.getParentPath(), df.getName()));
        }
        return null;
    }

    private List<FileItem> listFromAlist(Long engineId, String path, boolean refresh) {
        try {
            AlistClient client = engineService.getClient(engineId);
            List<FileItem> items = client.listAllFiles(path, refresh, 1000);
            for (FileItem item : items) {
                if (Boolean.TRUE.equals(item.getIsDir())) {
                    continue;
                }
                String url = MediaTypes.mediaUrl(engineId, item.getPath(), item.getName());
                item.setUrl(mediaAccessService.withAccessToken(url));
                if (MediaTypes.isImage(item.getName())) {
                    item.setThumbnailUrl(item.getUrl());
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
        int from = Math.max(0, (page - 1) * perPage);
        if (from >= items.size()) {
            return List.of();
        }
        return items.subList(from, Math.min(items.size(), from + perPage));
    }

    private Comparator<FileItem> buildComparator(String sortMode) {
        String mode = sortMode == null ? "name_asc" : sortMode;
        Comparator<FileItem> primary = mode.startsWith("time_")
                ? Comparator.comparing(item -> item.getModified() == null ? 0L : item.getModified())
                : Comparator.comparing(item -> item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT));
        if (mode.endsWith("_desc")) {
            primary = primary.reversed();
        }
        return Comparator.comparing(FileItem::getIsDir, Comparator.nullsLast(Boolean::compareTo)).reversed()
                .thenComparing(primary)
                .thenComparing(item -> item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT));
    }

    private FileItem toFileItem(DirFile df, Long engineId) {
        FileItem fi = new FileItem();
        fi.setName(df.getName());
        fi.setIsDir(Boolean.TRUE.equals(df.getIsDir()));
        fi.setSize(df.getSize());
        fi.setExt(df.getExt());
        String fullPath = PathUtils.join(df.getParentPath(), df.getName());
        fi.setPath(fullPath);
        fi.setModified(df.getModifiedTime());
        if (Boolean.TRUE.equals(df.getIsDir())) {
            String thumb = mediaAccessService.withAccessToken(df.getThumbnailUrl());
            fi.setUrl(thumb);
            fi.setThumbnailUrl(thumb);
        } else {
            String url = mediaAccessService.withAccessToken(MediaTypes.mediaUrl(engineId, fullPath, df.getName()));
            fi.setUrl(url);
            if (MediaTypes.isImage(df.getName())) {
                // 列表预览：优先 WebP 缩略图（>500KB 同步时生成），小图用原图
                if (df.getThumbnailUrl() != null && !df.getThumbnailUrl().isEmpty()) {
                    fi.setThumbnailUrl(mediaAccessService.withAccessToken(df.getThumbnailUrl()));
                } else {
                    fi.setThumbnailUrl(url);
                }
            } else if (MediaTypes.isVideo(df.getName())) {
                // 优先使用同步时写入的 coverUrl（可能已是封面图的 WebP）
                String cover = df.getCoverUrl();
                if (cover != null && !cover.isEmpty()) {
                    fi.setThumbnailUrl(mediaAccessService.withAccessToken(cover));
                } else if (df.getThumbnailUrl() != null && !df.getThumbnailUrl().isEmpty()) {
                    fi.setThumbnailUrl(mediaAccessService.withAccessToken(df.getThumbnailUrl()));
                }
            }
        }
        return fi;
    }

    private FileItem toFileItem(FileDirectory directory) {
        FileItem fi = new FileItem();
        fi.setName(directory.getName());
        fi.setIsDir(true);
        fi.setSize(0L);
        fi.setPath(directory.getPath());
        fi.setModified(directory.getModifiedTime());
        String thumb = directory.getThumbnailUrl();
        if (thumb == null || thumb.isEmpty()) {
            thumb = resolveDirThumbFromChildren(directory.getEngineId(), directory.getId());
        }
        thumb = mediaAccessService.withAccessToken(thumb);
        fi.setUrl(thumb);
        fi.setThumbnailUrl(thumb);
        return fi;
    }
}
