package com.xbrowse.service;

import com.xbrowse.entity.DirFile;
import com.xbrowse.entity.BrowseDirectory;
import com.xbrowse.entity.IndexedDirectory;
import com.xbrowse.repository.BrowseDirectoryRepository;
import com.xbrowse.repository.DirFileRepository;
import com.xbrowse.repository.IndexedDirectoryRepository;
import com.xbrowse.util.AlistClient;
import com.xbrowse.dto.FileItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class DirFileSyncService {

    private final DirFileRepository dirFileRepository;
    private final IndexedDirectoryRepository indexedDirectoryRepository;
    private final BrowseDirectoryRepository browseDirectoryRepository;
    private final AlistEngineService engineService;
    private final TransactionTemplate txTemplate;
    private final ThumbnailCacheService thumbnailCacheService;

    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"
    );

    public DirFileSyncService(DirFileRepository dirFileRepository,
                              IndexedDirectoryRepository indexedDirectoryRepository,
                              BrowseDirectoryRepository browseDirectoryRepository,
                              AlistEngineService engineService,
                              TransactionTemplate txTemplate, ThumbnailCacheService thumbnailCacheService) {
        this.dirFileRepository = dirFileRepository;
        this.indexedDirectoryRepository = indexedDirectoryRepository;
        this.browseDirectoryRepository = browseDirectoryRepository;
        this.engineService = engineService;
        this.txTemplate = txTemplate;
        this.thumbnailCacheService = thumbnailCacheService;
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void scheduledSync() {
        log.info("开始定时同步目录文件");
        long start = System.currentTimeMillis();
        try {
            List<Long> engineIds = engineService.listEngines().stream()
                    .map(e -> e.getId())
                    .toList();
            for (Long engineId : engineIds) {
                syncEngine(engineId);
            }
            log.info("定时同步目录文件完成, 耗时: {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("定时同步目录文件失败, 耗时: {}ms", System.currentTimeMillis() - start, e);
        }
    }

    public void syncEngine(Long engineId) {
        log.info("开始同步引擎: engineId={}", engineId);
        AlistClient client = engineService.getClient(engineId);
        List<BrowseDirectory> roots = browseDirectoryRepository.findByEngineId(engineId);
        if (roots.isEmpty()) {
            syncRecursive(engineId, "/", null, null, client);
        } else {
            for (BrowseDirectory root : roots) {
                syncRecursive(engineId, normalizePath(root.getPath()), root.getId(), null, client);
            }
        }
        log.info("引擎同步完成: engineId={}", engineId);
    }

    public void syncDirectory(Long engineId, String path) {
        log.info("手动同步目录: engineId={}, path={}", engineId, path);
        long start = System.currentTimeMillis();
        AlistClient client = engineService.getClient(engineId);
        String normalizedPath = normalizePath(path);
        Long browseDirectoryId = browseDirectoryRepository.findByEngineId(engineId).stream()
                .filter(dir -> normalizePath(dir.getPath()).equals(normalizedPath))
                .map(BrowseDirectory::getId)
                .findFirst()
                .orElse(null);
        syncRecursive(engineId, normalizedPath, browseDirectoryId, null, client);
        log.info("手动同步完成: engineId={}, path={}, 耗时: {}ms", engineId, path, System.currentTimeMillis() - start);
    }

    private IndexedDirectory syncRecursive(Long engineId, String path, Long browseDirectoryId, Long parentId, AlistClient client) {
        DirectorySyncResult result = syncOneDir(engineId, path, browseDirectoryId, parentId, client);
        if (result.thumbnail() != null) {
            updateThumbnail(result.directoryId(), result.thumbnail());
        }
        return indexedDirectoryRepository.findById(result.directoryId()).orElse(null);
    }

    private DirectorySyncResult syncOneDir(Long engineId, String path, Long browseDirectoryId, Long parentId, AlistClient client) {
        final String currentPath = normalizePath(path);
        path = currentPath;
        log.info("同步目录: engineId={}, path={}", engineId, path);
        List<FileItem> items;
        try {
            items = client.listFiles(path, true, 1, 1000);
        } catch (Exception e) {
            log.error("获取目录列表失败: engineId={}, path={}", engineId, path, e);
            IndexedDirectory directory = ensureDirectory(engineId, path, browseDirectoryId, parentId, null);
            return new DirectorySyncResult(directory.getId(), directory.getThumbnailUrl());
        }
        log.info("目录内容: engineId={}, path={}, items={}", engineId, path, items.size());

        // 用于保存数据库的缩略图 URL（可能是代理 URL 或缓存 URL）
        String dirThumbnail = null;
        // 第一个图片文件的路径（用于下载缓存）
        String firstImagePath = null;

        IndexedDirectory currentDirectory = ensureDirectory(engineId, path, browseDirectoryId, parentId, null);
        Long currentDirectoryId = currentDirectory.getId();
        pruneRemovedSubDirectories(engineId, currentDirectoryId, items);

        txTemplate.executeWithoutResult(status -> {
            dirFileRepository.deleteByDirectoryId(currentDirectoryId);
            dirFileRepository.deleteByEngineIdAndParentPath(engineId, currentPath);

            List<DirFile> toSave = new ArrayList<>();
            for (FileItem item : items) {
                if (Boolean.TRUE.equals(item.getIsDir())) {
                    continue;
                }
                DirFile df = new DirFile();
                df.setEngineId(engineId);
                df.setParentPath(currentPath);
                df.setDirectoryId(currentDirectoryId);
                df.setName(item.getName());
                df.setIsDir(false);
                df.setSize(item.getSize());
                df.setExt(item.getExt());
                df.setModifiedTime(item.getModified());
                toSave.add(df);
            }
            dirFileRepository.saveAll(toSave);
            log.info("目录数据已保存: engineId={}, path={}, count={}", engineId, currentPath, toSave.size());
        });

        // 在事务外寻找第一个图片文件作为目录缩略图
        for (FileItem item : items) {
            if (!Boolean.TRUE.equals(item.getIsDir()) && isImageFile(item.getName())) {
                firstImagePath = item.getPath();
                break;
            }
        }

        // 尝试缓存预览图到本地磁盘
        if (firstImagePath != null) {
            String cachedUrl = thumbnailCacheService.cacheThumbnail(engineId, firstImagePath, client);
            if (cachedUrl != null) {
                // 缓存成功，使用本地缓存 URL
                dirThumbnail = cachedUrl;
                log.info("目录预览图已缓存: engineId={}, path={}, cacheUrl={}", engineId, path, cachedUrl);
            } else {
                // 缓存失败，回退到代理 URL
                dirThumbnail = "/api/files/proxy/" + engineId + "/" + encodePath(firstImagePath);
                log.warn("目录预览图缓存失败，回退到代理URL: engineId={}, path={}", engineId, path);
            }
        }

        // 递归同步子目录，获取子目录的缩略图
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir())) {
                try {
                    DirectorySyncResult subResult = syncOneDir(engineId, item.getPath(), browseDirectoryId, currentDirectoryId, client);
                    if (subResult.thumbnail() != null) {
                        updateThumbnail(subResult.directoryId(), subResult.thumbnail());
                    }
                    if (dirThumbnail == null && subResult.thumbnail() != null) {
                        dirThumbnail = subResult.thumbnail();
                    }
                } catch (Exception e) {
                    log.error("同步子目录失败: engineId={}, path={}", engineId, item.getPath(), e);
                }
            }
        }

        // 如果当前目录没有图片，尝试使用子目录的缩略图
        return new DirectorySyncResult(currentDirectoryId, dirThumbnail);
    }

    private void pruneRemovedSubDirectories(Long engineId, Long parentId, List<FileItem> currentItems) {
        Set<String> existingDirNames = new HashSet<>();
        for (FileItem item : currentItems) {
            if (Boolean.TRUE.equals(item.getIsDir())) {
                existingDirNames.add(item.getName());
            }
        }

        List<IndexedDirectory> indexedChildren = indexedDirectoryRepository.findByEngineIdAndParentIdOrderByNameAsc(engineId, parentId);
        for (IndexedDirectory child : indexedChildren) {
            if (!existingDirNames.contains(child.getName())) {
                deleteIndexedDirectoryTree(engineId, child);
            }
        }
    }

    private void deleteIndexedDirectoryTree(Long engineId, IndexedDirectory directory) {
        String pathPrefix = directory.getPath().endsWith("/") ? directory.getPath() : directory.getPath() + "/";
        txTemplate.executeWithoutResult(status -> {
            dirFileRepository.deleteByDirectoryId(directory.getId());
            dirFileRepository.deleteByEngineIdAndParentPathStartingWith(engineId, pathPrefix);
            indexedDirectoryRepository.deleteByEngineIdAndPathStartingWith(engineId, pathPrefix);
            indexedDirectoryRepository.delete(directory);
        });
    }

    private IndexedDirectory ensureDirectory(Long engineId, String path, Long browseDirectoryId, Long parentId, Long modifiedTime) {
        return txTemplate.execute(status -> {
            Long resolvedParentId = parentId;
            if (resolvedParentId == null && !"/".equals(path)) {
                resolvedParentId = indexedDirectoryRepository.findByEngineIdAndPath(engineId, parentOf(path))
                        .map(IndexedDirectory::getId)
                        .orElse(null);
            }
            IndexedDirectory directory = indexedDirectoryRepository.findByEngineIdAndPath(engineId, path)
                    .orElseGet(IndexedDirectory::new);
            directory.setEngineId(engineId);
            directory.setPath(path);
            directory.setName(nameOf(path));
            directory.setBrowseDirectoryId(browseDirectoryId);
            directory.setParentId(resolvedParentId);
            directory.setModifiedTime(modifiedTime);
            return indexedDirectoryRepository.save(directory);
        });
    }

    private void updateThumbnail(Long directoryId, String thumbnail) {
        txTemplate.executeWithoutResult(status -> {
            indexedDirectoryRepository.findById(directoryId).ifPresent(directory -> {
                directory.setThumbnailUrl(thumbnail);
                indexedDirectoryRepository.save(directory);
            });
        });
    }

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

    private String nameOf(String path) {
        if (path.equals("/")) return "/";
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String parentOf(String path) {
        if (path.equals("/")) return "/";
        String parent = path.substring(0, path.lastIndexOf('/'));
        return parent.isEmpty() ? "/" : parent;
    }

    private boolean isImageFile(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase();
        return IMAGE_EXTS.contains(ext);
    }

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

    public List<DirFile> listByPath(Long engineId, String path) {
        String normalizedPath = normalizePath(path);
        return indexedDirectoryRepository.findByEngineIdAndPath(engineId, normalizedPath)
                .map(directory -> dirFileRepository.findByDirectoryIdOrderByNameAsc(directory.getId()))
                .orElseGet(() -> dirFileRepository.findByEngineIdAndParentPathOrderByIsDirDescNameAsc(engineId, normalizedPath));
    }

    public List<DirFile> search(Long engineId, String parentPath, String keyword) {
        String normalizedPath = normalizePath(parentPath);
        return indexedDirectoryRepository.findByEngineIdAndPath(engineId, normalizedPath)
                .map(directory -> dirFileRepository.searchByNameAndDirectoryId(directory.getId(), keyword))
                .orElseGet(() -> dirFileRepository.searchByNameAndParentPath(engineId, normalizedPath, keyword));
    }

    public boolean hasData(Long engineId, String path) {
        String normalizedPath = normalizePath(path);
        return indexedDirectoryRepository.findByEngineIdAndPath(engineId, normalizedPath).isPresent()
                || dirFileRepository.existsByEngineIdAndParentPath(engineId, normalizedPath);
    }

    private record DirectorySyncResult(Long directoryId, String thumbnail) {}
}
