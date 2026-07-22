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
import com.xbrowse.util.SyncScheduleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目录文件同步服务
 * <p>
 * 按每个浏览目录（browse_directory）独立调度同步：
 * INTERVAL（分/时/天/月）或 CRON，不再整批同时同步所有目录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirFileSyncService {

    /** 调度轮询间隔：1 分钟检查一次是否有目录到期 */
    private static final long SCHEDULER_TICK_MS = 60_000L;
    private static final long SCHEDULER_INITIAL_DELAY_MS = 15_000L;

    private static final int ALIST_LIST_PAGE = 1;
    private static final int ALIST_LIST_PER_PAGE = 1000;

    private final DirFileRepository dirFileRepository;
    private final FileDirectoryRepository fileDirectoryRepository;
    private final BrowseDirectoryRepository browseDirectoryRepository;
    private final AlistEngineService engineService;
    private final TransactionTemplate txTemplate;
    private final ThumbnailCacheService thumbnailCacheService;

    /** 防止同一浏览目录并发同步 */
    private final ConcurrentHashMap<Long, Boolean> syncingIds = new ConcurrentHashMap<>();

    /**
     * 全局同步串行：多目录定时/手动同时跑时，避免 SQLite 与图片缓存写互相卡住
     * （真正耗时的下载在 ThumbnailCacheService 内已有分片锁 + 并发上限）
     */
    private final Object syncGlobalLock = new Object();

    /**
     * 调度轮询：逐个检查到期的浏览目录并同步
     */
    @Scheduled(fixedDelay = SCHEDULER_TICK_MS, initialDelay = SCHEDULER_INITIAL_DELAY_MS)
    public void scheduledSync() {
        LocalDateTime now = LocalDateTime.now();
        List<BrowseDirectory> roots = browseDirectoryRepository.findAll();
        if (roots.isEmpty()) {
            return;
        }
        int dueCount = 0;
        for (BrowseDirectory root : roots) {
            if (!SyncScheduleUtils.isDue(root, now)) {
                continue;
            }
            dueCount++;
            try {
                syncBrowseDirectory(root.getId(), false);
            } catch (Exception e) {
                log.error("定时同步浏览目录失败: id={}, path={}", root.getId(), root.getPath(), e);
            }
        }
        if (dueCount > 0) {
            log.info("本轮到期同步目录数: {}", dueCount);
        }
    }

    /**
     * 同步指定引擎下所有浏览目录（手动，忽略计划）
     */
    public void syncEngine(Long engineId) {
        List<BrowseDirectory> roots = browseDirectoryRepository.findByEngineId(engineId);
        if (roots.isEmpty()) {
            log.info("引擎无浏览目录，跳过同步: engineId={}", engineId);
            return;
        }
        for (BrowseDirectory root : roots) {
            syncBrowseDirectory(root.getId(), true);
        }
    }

    /**
     * 按浏览目录 ID 同步（手动或定时）
     *
     * @param force true 忽略计划强制同步
     */
    public void syncBrowseDirectory(Long browseDirectoryId, boolean force) {
        if (browseDirectoryId == null) {
            return;
        }
        if (syncingIds.putIfAbsent(browseDirectoryId, Boolean.TRUE) != null) {
            log.info("浏览目录正在同步中，跳过: id={}", browseDirectoryId);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            // 串行执行实际同步，降低多任务写库/写缓存冲突
            synchronized (syncGlobalLock) {
                doSyncBrowseDirectory(browseDirectoryId, force, start);
            }
        } finally {
            syncingIds.remove(browseDirectoryId);
        }
    }

    private void doSyncBrowseDirectory(Long browseDirectoryId, boolean force, long start) {
        BrowseDirectory root = browseDirectoryRepository.findById(browseDirectoryId).orElse(null);
        if (root == null) {
            log.warn("浏览目录不存在: id={}", browseDirectoryId);
            return;
        }
        if (!force && BrowseDirectory.SYNC_MODE_NONE.equals(root.getSyncMode())) {
            log.info("浏览目录未启用同步: id={}, path={}", root.getId(), root.getPath());
            return;
        }
        if (!force && !SyncScheduleUtils.isDue(root, LocalDateTime.now())) {
            log.debug("浏览目录未到期: id={}, next={}", root.getId(), root.getNextSyncTime());
            return;
        }

        log.info("开始同步浏览目录: id={}, engineId={}, path={}, force={}",
                root.getId(), root.getEngineId(), root.getPath(), force);
        String rootPath = PathUtils.normalize(root.getPath());
        AlistClient client = engineService.getClient(root.getEngineId());
        syncRecursive(root.getEngineId(), rootPath, resolveParentId(root.getEngineId(), rootPath), client);

        LocalDateTime finished = LocalDateTime.now();
        root.setLastSyncTime(finished);
        if (!BrowseDirectory.SYNC_MODE_NONE.equals(root.getSyncMode())) {
            root.setNextSyncTime(SyncScheduleUtils.calcNextSyncTime(root, finished));
        } else {
            root.setNextSyncTime(null);
        }
        browseDirectoryRepository.save(root);
        log.info("浏览目录同步完成: id={}, path={}, 耗时: {}ms, next={}",
                root.getId(), root.getPath(), System.currentTimeMillis() - start, root.getNextSyncTime());
    }

    /**
     * 同步指定路径及其子目录（添加浏览目录后立即调用，不更新计划字段）
     */
    public void syncDirectory(Long engineId, String path) {
        log.info("手动同步路径: engineId={}, path={}", engineId, path);
        long start = System.currentTimeMillis();
        synchronized (syncGlobalLock) {
            String normalizedPath = PathUtils.normalize(path);
            AlistClient client = engineService.getClient(engineId);
            syncRecursive(engineId, normalizedPath, resolveParentId(engineId, normalizedPath), client);
        }
        log.info("路径同步完成: engineId={}, path={}, 耗时: {}ms", engineId, path, System.currentTimeMillis() - start);
    }

    /**
     * 添加后后台同步（异步，不阻塞保存接口）
     */
    @Async("syncTaskExecutor")
    public void syncBrowseDirectoryAfterSave(Long browseDirectoryId) {
        if (browseDirectoryId == null) {
            return;
        }
        try {
            log.info("后台开始同步新目录: id={}", browseDirectoryId);
            syncBrowseDirectory(browseDirectoryId, true);
        } catch (Exception e) {
            log.error("保存后后台同步失败: id={}", browseDirectoryId, e);
            browseDirectoryRepository.findById(browseDirectoryId).ifPresent(root -> {
                if (!BrowseDirectory.SYNC_MODE_NONE.equals(root.getSyncMode())) {
                    root.setNextSyncTime(SyncScheduleUtils.calcNextSyncTime(root, LocalDateTime.now()));
                    browseDirectoryRepository.save(root);
                }
            });
        }
    }

    /**
     * 手动触发后台同步（异步，接口立即返回）
     */
    @Async("syncTaskExecutor")
    public void syncBrowseDirectoryAsync(Long browseDirectoryId) {
        if (browseDirectoryId == null) {
            return;
        }
        try {
            log.info("后台手动同步: id={}", browseDirectoryId);
            syncBrowseDirectory(browseDirectoryId, true);
        } catch (Exception e) {
            log.error("后台手动同步失败: id={}", browseDirectoryId, e);
        }
    }

    private Long resolveParentId(Long engineId, String path) {
        if ("/".equals(path)) {
            return null;
        }
        return fileDirectoryRepository.findByEngineIdAndPath(engineId, PathUtils.parentOf(path))
                .map(FileDirectory::getId)
                .orElse(null);
    }

    private void syncRecursive(Long engineId, String path, Long parentId, AlistClient client) {
        String thumb = syncOneDir(engineId, path, parentId, client);
        if (thumb != null) {
            updateThumbnail(engineId, path, thumb);
        }
    }

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
        // 为图片文件生成列表缩略图并写回 dir_file.thumbnail_url
        cacheFileThumbnails(engineId, directoryId, items, client);
        // 本层有图则立刻写入目录预览，刷新页面也能看到（不必等整棵子树同步完）
        String dirThumbnail = resolveDirThumbnail(engineId, path, items, client);
        if (dirThumbnail != null) {
            updateThumbnail(engineId, path, dirThumbnail);
        }

        dirThumbnail = syncChildrenAndInheritThumb(engineId, directoryId, items, client, dirThumbnail);
        if (dirThumbnail != null) {
            updateThumbnail(engineId, path, dirThumbnail);
        } else {
            dirThumbnail = txTemplate.execute(status ->
                    fileDirectoryRepository.findByEngineIdAndPath(engineId, path)
                            .map(FileDirectory::getThumbnailUrl)
                            .orElse(null));
        }
        return dirThumbnail;
    }

    private Long persistDirListing(Long engineId, String path, Long parentId, List<FileItem> items) {
        return txTemplate.execute(status -> {
            FileDirectory directory = fileDirectoryRepository.findByEngineIdAndPath(engineId, path)
                    .orElseGet(FileDirectory::new);
            directory.setEngineId(engineId);
            directory.setParentId("/".equals(path) ? null : parentId);
            directory.setPath(path);
            directory.setName(PathUtils.nameOf(path));
            directory = fileDirectoryRepository.save(directory);

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
     * 同步当前目录下图片文件的列表缩略图，写入 dir_file.thumbnail_url
     * <p>
     * 磁盘写入在事务外完成；库表批量一次提交，避免「每张图一个事务」导致 SQLite 锁等待/假死。
     */
    private void cacheFileThumbnails(Long engineId, Long directoryId, List<FileItem> items, AlistClient client) {
        if (directoryId == null || items == null || items.isEmpty()) {
            return;
        }
        // name -> thumbUrl
        java.util.Map<String, String> thumbByName = new java.util.LinkedHashMap<>();
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir()) || !MediaTypes.isImage(item.getName())) {
                continue;
            }
            try {
                String thumbUrl = thumbnailCacheService.cacheThumbnail(engineId, item.getPath(), client);
                if (thumbUrl == null) {
                    continue;
                }
                item.setThumbnailUrl(thumbUrl);
                thumbByName.put(item.getName(), thumbUrl);
            } catch (Exception e) {
                log.warn("图片缩略图同步失败: engineId={}, path={}, err={}", engineId, item.getPath(), e.getMessage());
            }
        }
        if (thumbByName.isEmpty()) {
            return;
        }
        txTemplate.executeWithoutResult(status -> {
            for (java.util.Map.Entry<String, String> e : thumbByName.entrySet()) {
                DirFile df = dirFileRepository.findByDirectoryIdAndName(directoryId, e.getKey());
                if (df != null) {
                    df.setThumbnailUrl(e.getValue());
                    dirFileRepository.save(df);
                }
            }
        });
    }

    private String resolveDirThumbnail(Long engineId, String path, List<FileItem> items, AlistClient client) {
        // 优先复用本层已生成的文件缩略图；缓存繁忙时仍回退 proxy，保证预览可用
        for (FileItem item : items) {
            if (Boolean.TRUE.equals(item.getIsDir()) || !MediaTypes.isImage(item.getName())) {
                continue;
            }
            if (item.getThumbnailUrl() != null && !item.getThumbnailUrl().isEmpty()) {
                return item.getThumbnailUrl();
            }
            String cachedUrl = thumbnailCacheService.cacheThumbnail(engineId, item.getPath(), client);
            if (cachedUrl != null) {
                return cachedUrl;
            }
            // 写缓存失败/超时：用代理原图做目录预览，避免刷新后空白
            return MediaTypes.proxyUrl(engineId, item.getPath());
        }
        return null;
    }

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

    private void updateThumbnail(Long engineId, String path, String thumbnail) {
        txTemplate.executeWithoutResult(status ->
                fileDirectoryRepository.findByEngineIdAndPath(engineId, path).ifPresent(directory -> {
                    directory.setThumbnailUrl(thumbnail);
                    fileDirectoryRepository.save(directory);
                }));
    }

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
