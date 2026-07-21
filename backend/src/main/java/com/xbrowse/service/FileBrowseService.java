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
import java.util.Map;
import java.util.Optional;

/**
 * 文件浏览核心服务
 * <p>
 * 优先从本地 file_directory / dir_file 读取；
 * 库中无数据或 refresh=true 时直连 Alist。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileBrowseService {

    private final DirFileRepository dirFileRepository;
    private final FileDirectoryRepository fileDirectoryRepository;
    private final AlistEngineService engineService;

    /**
     * 浏览目录
     *
     * @param refresh  true 强制直连 Alist；false 优先本地库
     * @param sortMode 排序：name_asc / name_desc / time_asc / time_desc
     */
    public List<FileItem> listFiles(Long engineId, String path, boolean refresh, int page, int perPage, String sortMode) {
        path = PathUtils.normalize(path);
        page = Math.max(page, 1);
        perPage = Math.max(perPage, 1);
        log.debug("查询目录: engineId={}, path={}, refresh={}, page={}, perPage={}, sort={}",
                engineId, path, refresh, page, perPage, sortMode);

        List<FileItem> items;
        if (!refresh) {
            Optional<FileDirectory> directoryOpt = fileDirectoryRepository.findByEngineIdAndPath(engineId, path);
            if (directoryOpt.isPresent()) {
                items = loadFromDb(engineId, directoryOpt.get());
            } else {
                // 本地未同步时回退直连，便于管理端选目录
                items = listFromAlist(engineId, path, false);
            }
        } else {
            items = listFromAlist(engineId, path, true);
        }
        // 分页前关联同名封面，避免封面图与视频不在同一页时丢失
        applyVideoCoverThumbnails(engineId, items);
        return paginate(sortItems(items, sortMode), page, perPage);
    }

    /**
     * 在指定父路径下按名称模糊搜索（仅本地库）
     */
    public List<FileItem> searchFiles(Long engineId, String parentPath, String keyword) {
        parentPath = PathUtils.normalize(parentPath);
        Optional<FileDirectory> directoryOpt = fileDirectoryRepository.findByEngineIdAndPath(engineId, parentPath);
        if (directoryOpt.isEmpty()) {
            return List.of();
        }
        Long directoryId = directoryOpt.get().getId();
        List<FileItem> items = new ArrayList<>();
        items.addAll(fileDirectoryRepository.searchByNameAndParentId(engineId, directoryId, keyword).stream()
                .map(this::toFileItem)
                .toList());
        items.addAll(dirFileRepository.searchByNameAndDirectoryId(directoryId, keyword).stream()
                .map(df -> toFileItem(df, engineId))
                .toList());
        applyVideoCoverThumbnails(engineId, items);
        return sortItems(items, "name_asc");
    }

    /**
     * 同目录下若存在与视频同名的图片（扩展名不同），则作为视频列表预览图。
     * 例如：demo.mp4 + demo.jpg → 使用 demo.jpg 作为封面。
     */
    private void applyVideoCoverThumbnails(Long engineId, List<FileItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        // baseName(小写) -> 封面图项（多图时取优先级更高的）
        Map<String, FileItem> coverByBase = new HashMap<>();
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir()) || item.getName() == null) {
                continue;
            }
            if (!MediaTypes.isImage(item.getName())) {
                continue;
            }
            String base = baseNameWithoutExt(item.getName()).toLowerCase();
            if (base.isEmpty()) {
                continue;
            }
            FileItem existing = coverByBase.get(base);
            if (existing == null || coverImagePriority(item.getName()) < coverImagePriority(existing.getName())) {
                coverByBase.put(base, item);
            }
        }
        if (coverByBase.isEmpty()) {
            return;
        }
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir()) || item.getName() == null) {
                continue;
            }
            if (!MediaTypes.isVideo(item.getName())) {
                continue;
            }
            FileItem cover = coverByBase.get(baseNameWithoutExt(item.getName()).toLowerCase());
            if (cover == null) {
                continue;
            }
            String thumb = cover.getUrl();
            if (thumb == null || thumb.isEmpty()) {
                thumb = MediaTypes.proxyUrl(engineId, cover.getPath());
            }
            item.setThumbnailUrl(thumb);
        }
    }

    /**
     * 去掉最后一个扩展名，得到主文件名
     */
    private String baseNameWithoutExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    /**
     * 封面图扩展名优先级（数值越小越优先）
     */
    private int coverImagePriority(String fileName) {
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

    /**
     * 获取目录预览图 URL（优先使用同步时写入的缓存地址）
     */
    public String getDirThumbnail(Long engineId, String dirPath) {
        return fileDirectoryRepository.findByEngineIdAndPath(engineId, PathUtils.normalize(dirPath))
                .map(FileDirectory::getThumbnailUrl)
                .orElse(null);
    }

    /**
     * 判断是否为图片文件
     */
    public boolean isImageFile(String fileName) {
        return MediaTypes.isImage(fileName);
    }

    /**
     * 判断是否为视频文件
     */
    public boolean isVideoFile(String fileName) {
        return MediaTypes.isVideo(fileName);
    }

    /**
     * 从本地库加载某目录下的子目录与文件
     */
    private List<FileItem> loadFromDb(Long engineId, FileDirectory directory) {
        List<FileItem> items = new ArrayList<>();
        items.addAll(fileDirectoryRepository.findByEngineIdAndParentId(engineId, directory.getId()).stream()
                .map(this::toFileItem)
                .toList());
        items.addAll(dirFileRepository.findByDirectoryId(directory.getId()).stream()
                .map(df -> toFileItem(df, engineId))
                .toList());
        return items;
    }

    /**
     * 直连 Alist 拉取目录列表，并为媒体文件填充访问 URL
     */
    private List<FileItem> listFromAlist(Long engineId, String path, boolean refresh) {
        try {
            AlistClient client = engineService.getClient(engineId);
            List<FileItem> items = client.listFiles(path, refresh, 1, 1000);
            for (FileItem item : items) {
                if (!Boolean.TRUE.equals(item.getIsDir())) {
                    item.setUrl(MediaTypes.mediaUrl(engineId, item.getPath(), item.getName()));
                }
            }
            return items;
        } catch (Exception e) {
            log.error("直连 Alist 获取目录失败: engineId={}, path={}", engineId, path, e);
            return List.of();
        }
    }

    /**
     * 排序：目录优先，再按名称或时间
     */
    private List<FileItem> sortItems(List<FileItem> items, String sortMode) {
        List<FileItem> sorted = new ArrayList<>(items);
        sorted.sort(buildComparator(sortMode));
        return sorted;
    }

    /**
     * 内存分页
     */
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
                : Comparator.comparing(item -> item.getName() == null ? "" : item.getName().toLowerCase());
        if (mode.endsWith("_desc")) {
            primary = primary.reversed();
        }
        return Comparator.comparing(FileItem::getIsDir, Comparator.nullsLast(Boolean::compareTo)).reversed()
                .thenComparing(primary)
                .thenComparing(item -> item.getName() == null ? "" : item.getName().toLowerCase());
    }

    /**
     * DirFile 转 FileItem，并为媒体文件生成 proxy/stream URL
     */
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
            fi.setUrl(df.getThumbnailUrl());
        } else {
            fi.setUrl(MediaTypes.mediaUrl(engineId, fullPath, df.getName()));
        }
        return fi;
    }

    /**
     * FileDirectory 转 FileItem（目录项）
     */
    private FileItem toFileItem(FileDirectory directory) {
        FileItem fi = new FileItem();
        fi.setName(directory.getName());
        fi.setIsDir(true);
        fi.setSize(0L);
        fi.setPath(directory.getPath());
        fi.setModified(directory.getModifiedTime());
        fi.setUrl(directory.getThumbnailUrl());
        return fi;
    }
}
