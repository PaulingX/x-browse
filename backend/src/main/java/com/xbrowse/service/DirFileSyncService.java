package com.xbrowse.service;

import com.xbrowse.entity.DirFile;
import com.xbrowse.entity.FileDirectory;
import com.xbrowse.repository.DirFileRepository;
import com.xbrowse.repository.FileDirectoryRepository;
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
    private final FileDirectoryRepository fileDirectoryRepository;
    private final AlistEngineService engineService;
    private final TransactionTemplate txTemplate;
    private final ThumbnailCacheService thumbnailCacheService;

    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"
    );

    public DirFileSyncService(DirFileRepository dirFileRepository, FileDirectoryRepository fileDirectoryRepository, AlistEngineService engineService,
                               TransactionTemplate txTemplate, ThumbnailCacheService thumbnailCacheService) {
        this.dirFileRepository = dirFileRepository;
        this.fileDirectoryRepository = fileDirectoryRepository;
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
                log.info("开始同步引擎: engineId={}", engineId);
                AlistClient client = engineService.getClient(engineId);
                syncRecursive(engineId, "/", null, client);
                log.info("引擎同步完成: engineId={}", engineId);
            }
            log.info("定时同步目录文件完成, 耗时: {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("定时同步目录文件失败, 耗时: {}ms", System.currentTimeMillis() - start, e);
        }
    }

    public void syncDirectory(Long engineId, String path) {
        log.info("手动同步目录: engineId={}, path={}", engineId, path);
        long start = System.currentTimeMillis();
        AlistClient client = engineService.getClient(engineId);
        String normalizedPath = normalizePath(path);
        Long parentId = normalizedPath.equals("/") ? null : fileDirectoryRepository.findByEngineIdAndPath(engineId, parentOf(normalizedPath))
                .map(FileDirectory::getId)
                .orElse(null);
        syncRecursive(engineId, normalizedPath, parentId, client);
        log.info("手动同步完成: engineId={}, path={}, 耗时: {}ms", engineId, path, System.currentTimeMillis() - start);
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
            items = client.listFiles(path, true, 1, 1000);
        } catch (Exception e) {
            log.error("获取目录列表失败: engineId={}, path={}", engineId, path, e);
            return null;
        }
        log.info("目录内容: engineId={}, path={}, items={}", engineId, path, items.size());

        // 用于保存数据库的缩略图 URL（可能是代理 URL 或缓存 URL）
        String dirThumbnail = null;
        // 第一个图片文件的路径（用于下载缓存）
        String firstImagePath = null;

        Long directoryId = txTemplate.execute(status -> {
            FileDirectory directory = fileDirectoryRepository.findByEngineIdAndPath(engineId, path)
                    .orElseGet(FileDirectory::new);
            directory.setEngineId(engineId);
            directory.setParentId(path.equals("/") ? null : parentId);
            directory.setPath(path);
            directory.setName(nameOf(path));
            directory = fileDirectoryRepository.save(directory);

            dirFileRepository.deleteByDirectoryId(directory.getId());

            List<DirFile> toSave = new ArrayList<>();
            List<FileDirectory> dirsToSave = new ArrayList<>();
            Set<String> currentDirPaths = new HashSet<>();
            for (FileItem item : items) {
                if (item.getIsDir()) {
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
                    toSave.add(df);
                }
            }
            deleteMissingChildDirs(engineId, directory.getId(), currentDirPaths);
            fileDirectoryRepository.saveAll(dirsToSave);
            dirFileRepository.saveAll(toSave);
            log.info("目录数据已保存: engineId={}, path={}, dirs={}, files={}", engineId, path, dirsToSave.size(), toSave.size());
            return directory.getId();
        });

        // 在事务外寻找第一个图片文件作为目录缩略图
        for (FileItem item : items) {
            if (!item.getIsDir() && isImageFile(item.getName())) {
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
            if (item.getIsDir()) {
                try {
                    String subThumb = syncOneDir(engineId, item.getPath(), directoryId, client);
                    if (dirThumbnail == null && subThumb != null) {
                        dirThumbnail = subThumb;
                    }
                } catch (Exception e) {
                    log.error("同步子目录失败: engineId={}, path={}", engineId, item.getPath(), e);
                }
            }
        }

        // 如果当前目录没有图片，尝试使用子目录的缩略图
        if (dirThumbnail == null) {
            dirThumbnail = txTemplate.execute(status -> {
                return fileDirectoryRepository.findByEngineIdAndPath(engineId, path)
                        .map(FileDirectory::getThumbnailUrl)
                        .orElse(null);
            });
        }

        return dirThumbnail;
    }

    private void updateThumbnail(Long engineId, String path, String thumbnail) {
        txTemplate.executeWithoutResult(status -> {
            fileDirectoryRepository.findByEngineIdAndPath(engineId, path).ifPresent(directory -> {
                directory.setThumbnailUrl(thumbnail);
                fileDirectoryRepository.save(directory);
            });
        });
    }

    private void deleteMissingChildDirs(Long engineId, Long parentId, Set<String> currentDirPaths) {
        List<FileDirectory> existingDirs = fileDirectoryRepository.findByEngineIdAndParentId(engineId, parentId);
        for (FileDirectory existingDir : existingDirs) {
            if (!currentDirPaths.contains(existingDir.getPath())) {
                List<Long> subtreeIds = new ArrayList<>();
                subtreeIds.add(existingDir.getId());
                subtreeIds.addAll(fileDirectoryRepository.findByEngineIdAndPathStartingWith(engineId, existingDir.getPath() + "/").stream()
                        .map(FileDirectory::getId)
                        .toList());
                dirFileRepository.deleteByDirectoryIdIn(subtreeIds);
                fileDirectoryRepository.deleteAllById(subtreeIds);
            }
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private String parentOf(String path) {
        if (path.equals("/")) return "/";
        String p = path.substring(0, path.lastIndexOf('/'));
        return p.isEmpty() ? "/" : p;
    }

    private String nameOf(String path) {
        if (path.equals("/")) return "/";
        return path.substring(path.lastIndexOf('/') + 1);
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
        return fileDirectoryRepository.findByEngineIdAndPath(engineId, normalizePath(path))
                .map(directory -> dirFileRepository.findByDirectoryId(directory.getId()))
                .orElseGet(List::of);
    }

    public List<DirFile> search(Long engineId, String parentPath, String keyword) {
        return fileDirectoryRepository.findByEngineIdAndPath(engineId, normalizePath(parentPath))
                .map(directory -> dirFileRepository.searchByNameAndDirectoryId(directory.getId(), keyword))
                .orElseGet(List::of);
    }

    public boolean hasData(Long engineId, String path) {
        return fileDirectoryRepository.findByEngineIdAndPath(engineId, normalizePath(path)).isPresent();
    }
}
