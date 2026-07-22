package com.xbrowse.service;

import com.xbrowse.config.AppConfig;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final AppConfig appConfig;

    /** 防止同一浏览目录并发同步 */
    private final ConcurrentHashMap<Long, Boolean> syncingIds = new ConcurrentHashMap<>();

    /** 全局同步串行：多目录定时/手动同时跑时，避免 SQLite 写冲突 */
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
            items = client.listAllFiles(path, true, ALIST_LIST_PER_PAGE);
        } catch (Exception e) {
            log.error("获取目录列表失败: engineId={}, path={}", engineId, path, e);
            return null;
        }
        log.info("目录内容: engineId={}, path={}, items={}", engineId, path, items.size());

        Long directoryId = persistDirListing(engineId, path, parentId, items);
        // 列表/目录预览直接用原图代理，不生成缩略图
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

            // 增量同步：文件系统有而库无则入库；库有而文件系统无则删除
            List<DirFile> existingFiles = dirFileRepository.findByDirectoryId(directory.getId());
            Map<String, DirFile> existingByName = new HashMap<>();
            for (DirFile df : existingFiles) {
                if (df.getName() != null) {
                    existingByName.put(df.getName(), df);
                }
            }

            Set<String> currentFileNames = new HashSet<>();
            Set<String> currentDirPaths = new HashSet<>();
            List<DirFile> filesToSave = new ArrayList<>();
            List<FileDirectory> dirsToSave = new ArrayList<>();
            int inserted = 0;
            int updated = 0;

            for (FileItem item : items) {
                if (shouldSkipItem(item)) {
                    continue;
                }
                if (Boolean.TRUE.equals(item.getIsDir())) {
                    currentDirPaths.add(PathUtils.normalize(item.getPath()));
                    FileDirectory subDir = fileDirectoryRepository.findByEngineIdAndPath(engineId, item.getPath())
                            .orElseGet(FileDirectory::new);
                    subDir.setEngineId(engineId);
                    subDir.setParentId(directory.getId());
                    subDir.setPath(item.getPath());
                    subDir.setName(item.getName());
                    subDir.setModifiedTime(item.getModified());
                    dirsToSave.add(subDir);
                } else {
                    currentFileNames.add(item.getName());
                    DirFile df = existingByName.get(item.getName());
                    if (df == null) {
                        df = new DirFile();
                        df.setEngineId(engineId);
                        df.setDirectoryId(directory.getId());
                        df.setParentPath(path);
                        df.setName(item.getName());
                        df.setIsDir(false);
                        inserted++;
                    } else {
                        updated++;
                    }
                    df.setSize(item.getSize());
                    df.setExt(item.getExt());
                    df.setModifiedTime(item.getModified());
                    df.setSyncTime(LocalDateTime.now());
                    filesToSave.add(df);
                }
            }

            // 删除库中有、文件系统没有的文件
            List<DirFile> toDelete = new ArrayList<>();
            for (DirFile df : existingFiles) {
                if (df.getName() == null || !currentFileNames.contains(df.getName())) {
                    toDelete.add(df);
                }
            }
            if (!toDelete.isEmpty()) {
                dirFileRepository.deleteAll(toDelete);
            }

            deleteMissingChildDirs(engineId, directory.getId(), currentDirPaths);
            fileDirectoryRepository.saveAll(dirsToSave);
            dirFileRepository.saveAll(filesToSave);
            log.info("目录内容已同步: engineId={}, path={}, dirs={}, files(+{}/~{}/-{}), totalFiles={}",
                    engineId, path, dirsToSave.size(),
                    inserted, updated, toDelete.size(), filesToSave.size());
            return directory.getId();
        });
    }
    /**
     * 是否跳过入库/同步（点目录、配置的忽略后缀）
     */
    private boolean shouldSkipItem(FileItem item) {
        if (item == null || item.getName() == null) {
            return true;
        }
        if (Boolean.TRUE.equals(item.getIsDir())) {
            return appConfig.shouldIgnoreDirName(item.getName());
        }
        return appConfig.shouldIgnoreFileName(item.getName());
    }

    /** 目录预览：本层第一张图片的原图代理 URL */
    private String resolveDirThumbnail(Long engineId, String path, List<FileItem> items, AlistClient client) {
        for (FileItem item : items) {
            if (shouldSkipItem(item) || Boolean.TRUE.equals(item.getIsDir()) || !MediaTypes.isImage(item.getName())) {
                continue;
            }
            return MediaTypes.proxyUrl(engineId, item.getPath());
        }
        return null;
    }

    private String syncChildrenAndInheritThumb(Long engineId, Long directoryId, List<FileItem> items,
                                               AlistClient client, String dirThumbnail) {
        for (FileItem item : items) {
            if (!Boolean.TRUE.equals(item.getIsDir()) || shouldSkipItem(item)) {
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

    /**
     * 删除父目录下：数据库有、文件系统已不存在的子目录（含子树文件与目录）
     */
    private void deleteMissingChildDirs(Long engineId, Long parentId, Set<String> currentDirPaths) {
        if (parentId == null) {
            return;
        }
        List<FileDirectory> existingDirs = fileDirectoryRepository.findByEngineIdAndParentId(engineId, parentId);
        if (existingDirs == null || existingDirs.isEmpty()) {
            return;
        }
        Set<String> livePaths = new HashSet<>();
        if (currentDirPaths != null) {
            for (String p : currentDirPaths) {
                if (p != null && !p.isBlank()) {
                    livePaths.add(PathUtils.normalize(p));
                }
            }
        }
        List<FileDirectory> missing = new ArrayList<>();
        for (FileDirectory existingDir : existingDirs) {
            String dbPath = PathUtils.normalize(existingDir.getPath());
            if (livePaths.contains(dbPath)) {
                continue;
            }
            missing.add(existingDir);
        }
        if (missing.isEmpty()) {
            return;
        }
        for (FileDirectory missingDir : missing) {
            deleteDirectorySubtree(engineId, missingDir);
        }
        log.info("已删除文件系统中不存在的子目录: engineId={}, parentId={}, count={}, paths={}",
                engineId, parentId, missing.size(),
                missing.stream().map(FileDirectory::getPath).toList());
    }

    /**
     * 递归删除目录节点及其全部子孙目录、文件记录
     */
    private void deleteDirectorySubtree(Long engineId, FileDirectory root) {
        if (root == null || root.getId() == null) {
            return;
        }
        String rootPath = PathUtils.normalize(root.getPath());
        List<Long> subtreeIds = new ArrayList<>();
        subtreeIds.add(root.getId());
        // 子孙目录：path 以 rootPath + "/" 开头
        String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
        List<FileDirectory> descendants = fileDirectoryRepository
                .findByEngineIdAndPathStartingWith(engineId, prefix);
        for (FileDirectory d : descendants) {
            if (d.getId() != null && !d.getId().equals(root.getId())) {
                subtreeIds.add(d.getId());
            }
        }
        // 也按 parent 链再扫一层，防止 path 前缀不一致漏删
        collectChildDirectoryIds(engineId, root.getId(), subtreeIds);

        dirFileRepository.deleteByDirectoryIdIn(subtreeIds);
        fileDirectoryRepository.deleteAllById(subtreeIds);
        log.debug("删除目录子树: engineId={}, path={}, nodes={}", engineId, rootPath, subtreeIds.size());
    }

    private void collectChildDirectoryIds(Long engineId, Long parentId, List<Long> out) {
        List<FileDirectory> children = fileDirectoryRepository.findByEngineIdAndParentId(engineId, parentId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (FileDirectory child : children) {
            if (child.getId() == null) {
                continue;
            }
            if (!out.contains(child.getId())) {
                out.add(child.getId());
                collectChildDirectoryIds(engineId, child.getId(), out);
            }
        }
    }
}