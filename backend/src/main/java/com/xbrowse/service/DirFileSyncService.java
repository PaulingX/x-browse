package com.xbrowse.service;

import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.BrowseDirectory;
import com.xbrowse.entity.DirFile;
import com.xbrowse.entity.FileDirectory;
import com.xbrowse.repository.BrowseDirectoryRepository;
import com.xbrowse.repository.DirFileRepository;
import com.xbrowse.repository.FileDirectoryRepository;
import com.xbrowse.util.AlistClient;
import com.xbrowse.util.MediaTypes;
import com.xbrowse.util.PathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目录文件同步服务
 * <p>
 * 将 Alist 上配置的浏览目录（browse_directory）递归同步到本地
 * file_directory / dir_file，供浏览接口离线读取。
 * <p>
 * 注意：只同步已配置的浏览根路径，不扫描整个引擎根目录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirFileSyncService {

    /** 定时同步间隔：5 分钟 */
    private static final long SYNC_FIXED_DELAY_MS = 300_000L;

    /** 启动后首次同步延迟：30 秒 */
    private static final long SYNC_INITIAL_DELAY_MS = 30_000L;

    private static final int ALIST_LIST_PAGE = 1;
    private static final int ALIST_LIST_PER_PAGE = 1000;

    private final DirFileRepository dirFileRepository;
    private final FileDirectoryRepository fileDirectoryRepository;
    private final BrowseDirectoryRepository browseDirectoryRepository;
    private final AlistEngineService engineService;
    private final TransactionTemplate txTemplate;
    private final ThumbnailCacheService thumbnailCacheService;

    /**
     * 定时同步所有已配置的浏览目录
     */
    @Scheduled(fixedDelay = SYNC_FIXED_DELAY_MS, initialDelay = SYNC_INITIAL_DELAY_MS)
    public void scheduledSync() {
        log.info("开始定时同步目录文件");
        long start = System.currentTimeMillis();
        try {
            List<BrowseDirectory> roots = browseDirectoryRepository.findAll();
            if (roots.isEmpty()) {
                log.info("无浏览目录配置，跳过同步");
                return;
            }
            Map<Long, List<BrowseDirectory>> byEngine = groupByEngine(roots);
            for (Map.Entry<Long, List<BrowseDirectory>> entry : byEngine.entrySet()) {
                syncRoots(entry.getKey(), entry.getValue());
            }
            log.info("定时同步目录文件完成, 耗时: {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("定时同步目录文件失败, 耗时: {}ms", System.currentTimeMillis() - start, e);
        }
    }

    /**
     * 同步指定引擎下已配置的浏览目录（不整库扫描引擎根路径）
     */
    public void syncEngine(Long engineId) {
        List<BrowseDirectory> roots = browseDirectoryRepository.findByEngineId(engineId);
        if (roots.isEmpty()) {
            log.info("引擎无浏览目录，跳过同步: engineId={}", engineId);
            return;
        }
        syncRoots(engineId, roots);
    }

    /**
     * 同步指定路径及其子目录（添加浏览目录后会调用）
     */
    public void syncDirectory(Long engineId, String path) {
        log.info("手动同步目录: engineId={}, path={}", engineId, path);
        long start = System.currentTimeMillis();
        String normalizedPath = PathUtils.normalize(path);
        AlistClient client = engineService.getClient(engineId);
        syncRecursive(engineId, normalizedPath, resolveParentId(engineId, normalizedPath), client);
        log.info("手动同步完成: engineId={}, path={}, 耗时: {}ms", engineId, path, System.currentTimeMillis() - start);
    }

    /**
     * 按引擎同步一组浏览根目录
     */
    private void syncRoots(Long engineId, List<BrowseDirectory> roots) {
        log.info("同步引擎浏览目录: engineId={}, roots={}", engineId, roots.size());
        long start = System.currentTimeMillis();
        AlistClient client = engineService.getClient(engineId);
        for (BrowseDirectory root : roots) {
            String rootPath = PathUtils.normalize(root.getPath());
            syncRecursive(engineId, rootPath, resolveParentId(engineId, rootPath), client);
        }
        log.info("引擎浏览目录同步完成: engineId={}, 耗时: {}ms", engineId, System.currentTimeMillis() - start);
    }

    /**
     * 按引擎 ID 分组浏览目录
     */
    private Map<Long, List<BrowseDirectory>> groupByEngine(List<BrowseDirectory> roots) {
        Map<Long, List<BrowseDirectory>> byEngine = new LinkedHashMap<>();
        for (BrowseDirectory root : roots) {
            byEngine.computeIfAbsent(root.getEngineId(), k -> new ArrayList<>()).add(root);
        }
        return byEngine;
    }

    /**
     * 解析路径在 file_directory 中的父节点 ID（根路径为 null）
     */
    private Long resolveParentId(Long engineId, String path) {
        if ("/".equals(path)) {
            return null;
        }
        return fileDirectoryRepository.findByEngineIdAndPath(engineId, PathUtils.parentOf(path))
                .map(FileDirectory::getId)
                .orElse(null);
    }

    /**
     * 同步一个目录树节点，并写回缩略图
     */
    private void syncRecursive(Long engineId, String path, Long parentId, AlistClient client) {
        String thumb = syncOneDir(engineId, path, parentId, client);
        if (thumb != null) {
            updateThumbnail(engineId, path, thumb);
        }
    }

    /**
     * 同步单个目录：拉取列表 → 落库 → 解析缩略图 → 递归子目录
     *
     * @return 当前目录可用的缩略图 URL（自身或继承自子目录）
     */
    private String syncOneDir(Long engineId, String path, Long parentId, AlistClient client) {
        log.info("同步目录: engineId={}, path={}", engineId, path);
        List<FileItem> items;
        try {
            items = client.listFiles(path, true, ALIST_LIST_PAGE, ALIST_LIST_PER_PAGE);
        } catch (Exception e) {
            log.error("获取目录列表失败: engineId={}, path={}", engineId, path, e);
            return null;
        }
        log.info("目录内容: engineId={}, path={}, items={}", engineId, path, items.size());

        Long directoryId = persistDirListing(engineId, path, parentId, items);
        String dirThumbnail = resolveDirThumbnail(engineId, path, items, client);
        // 递归子目录，若当前无图则继承子目录缩略图
        dirThumbnail = syncChildrenAndInheritThumb(engineId, directoryId, items, client, dirThumbnail);

        if (dirThumbnail == null) {
            dirThumbnail = txTemplate.execute(status ->
                    fileDirectoryRepository.findByEngineIdAndPath(engineId, path)
                            .map(FileDirectory::getThumbnailUrl)
                            .orElse(null));
        }
        return dirThumbnail;
    }

    /**
     * 将 Alist 列表写入 file_directory / dir_file，并删除已不存在的子目录
     */
    private Long persistDirListing(Long engineId, String path, Long parentId, List<FileItem> items) {
        return txTemplate.execute(status -> {
            FileDirectory directory = fileDirectoryRepository.findByEngineIdAndPath(engineId, path)
                    .orElseGet(FileDirectory::new);
            directory.setEngineId(engineId);
            directory.setParentId("/".equals(path) ? null : parentId);
            directory.setPath(path);
            directory.setName(PathUtils.nameOf(path));
            directory = fileDirectoryRepository.save(directory);

            // 文件列表全量替换
            dirFileRepository.deleteByDirectoryId(directory.getId());

            List<DirFile> filesToSave = new ArrayList<>();
            List<FileDirectory> dirsToSave = new ArrayList<>();
            Set<String> currentDirPaths = new HashSet<>();
            for (FileItem item : items) {
                if (Boolean.TRUE.equals(item.getIsDir())) {
                    currentDirPaths.add(item.getPath());
                    FileDirectory subDir = fileDirectoryRepository.findByEngineIdAndPath(engineId, item.getPath())
                            .orElseGet(FileDirectory::new);
                    subDir.setEngineId(engineId);
                    subDir.setParentId(directory.getId());
                    subDir.setPath(item.getPath());
                    subDir.setName(item.getName());
                    subDir.setModifiedTime(item.getModified());
                    dirsToSave.add(subDir);
                } else {
                    DirFile df = new DirFile();
                    df.setEngineId(engineId);
                    df.setDirectoryId(directory.getId());
                    df.setParentPath(path);
                    df.setName(item.getName());
                    df.setIsDir(false);
                    df.setSize(item.getSize());
                    df.setExt(item.getExt());
                    df.setModifiedTime(item.getModified());
                    filesToSave.add(df);
                }
            }
            deleteMissingChildDirs(engineId, directory.getId(), currentDirPaths);
            fileDirectoryRepository.saveAll(dirsToSave);
            dirFileRepository.saveAll(filesToSave);
            log.info("目录数据已保存: engineId={}, path={}, dirs={}, files={}",
                    engineId, path, dirsToSave.size(), filesToSave.size());
            return directory.getId();
        });
    }

    /**
     * 取目录内第一张图片作为预览图；优先本地缓存，失败则回退代理 URL
     */
    private String resolveDirThumbnail(Long engineId, String path, List<FileItem> items, AlistClient client) {
        String firstImagePath = null;
        for (FileItem item : items) {
            if (!Boolean.TRUE.equals(item.getIsDir()) && MediaTypes.isImage(item.getName())) {
                firstImagePath = item.getPath();
                break;
            }
        }
        if (firstImagePath == null) {
            return null;
        }
        String cachedUrl = thumbnailCacheService.cacheThumbnail(engineId, firstImagePath, client);
        if (cachedUrl != null) {
            log.info("目录预览图已缓存: engineId={}, path={}, cacheUrl={}", engineId, path, cachedUrl);
            return cachedUrl;
        }
        log.warn("目录预览图缓存失败，回退到代理URL: engineId={}, path={}", engineId, path);
        return MediaTypes.proxyUrl(engineId, firstImagePath);
    }

    /**
     * 递归同步子目录；当前目录无缩略图时继承子目录缩略图
     */
    private String syncChildrenAndInheritThumb(Long engineId, Long directoryId, List<FileItem> items,
                                               AlistClient client, String dirThumbnail) {
        for (FileItem item : items) {
            if (!Boolean.TRUE.equals(item.getIsDir())) {
                continue;
            }
            try {
                String subThumb = syncOneDir(engineId, item.getPath(), directoryId, client);
                if (dirThumbnail == null && subThumb != null) {
                    dirThumbnail = subThumb;
                }
            } catch (Exception e) {
                log.error("同步子目录失败: engineId={}, path={}", engineId, item.getPath(), e);
            }
        }
        return dirThumbnail;
    }

    /**
     * 更新目录缩略图字段
     */
    private void updateThumbnail(Long engineId, String path, String thumbnail) {
        txTemplate.executeWithoutResult(status ->
                fileDirectoryRepository.findByEngineIdAndPath(engineId, path).ifPresent(directory -> {
                    directory.setThumbnailUrl(thumbnail);
                    fileDirectoryRepository.save(directory);
                }));
    }

    /**
     * 删除 Alist 侧已不存在的子目录及其子树、文件
     */
    private void deleteMissingChildDirs(Long engineId, Long parentId, Set<String> currentDirPaths) {
        List<FileDirectory> existingDirs = fileDirectoryRepository.findByEngineIdAndParentId(engineId, parentId);
        for (FileDirectory existingDir : existingDirs) {
            if (currentDirPaths.contains(existingDir.getPath())) {
                continue;
            }
            List<Long> subtreeIds = new ArrayList<>();
            subtreeIds.add(existingDir.getId());
            subtreeIds.addAll(fileDirectoryRepository
                    .findByEngineIdAndPathStartingWith(engineId, existingDir.getPath() + "/")
                    .stream()
                    .map(FileDirectory::getId)
                    .toList());
            dirFileRepository.deleteByDirectoryIdIn(subtreeIds);
            fileDirectoryRepository.deleteAllById(subtreeIds);
        }
    }
}
