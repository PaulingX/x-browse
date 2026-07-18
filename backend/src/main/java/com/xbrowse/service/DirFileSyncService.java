package com.xbrowse.service;

import com.xbrowse.entity.DirFile;
import com.xbrowse.repository.DirFileRepository;
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
    private final AlistEngineService engineService;
    private final TransactionTemplate txTemplate;

    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"
    );

    public DirFileSyncService(DirFileRepository dirFileRepository, AlistEngineService engineService, TransactionTemplate txTemplate) {
        this.dirFileRepository = dirFileRepository;
        this.engineService = engineService;
        this.txTemplate = txTemplate;
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
                syncRecursive(engineId, "/", client);
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
        syncRecursive(engineId, path, client);
        log.info("手动同步完成: engineId={}, path={}, 耗时: {}ms", engineId, path, System.currentTimeMillis() - start);
    }

    private void syncRecursive(Long engineId, String path, AlistClient client) {
        String thumb = syncOneDir(engineId, path, client);
        if (thumb != null) {
            updateThumbnail(engineId, path, thumb);
        }
    }

    private String syncOneDir(Long engineId, String path, AlistClient client) {
        log.debug("同步目录: engineId={}, path={}", engineId, path);
        List<FileItem> items = client.listFiles(path, true, 1, 1000);
        log.debug("目录内容: engineId={}, path={}, items={}", engineId, path, items.size());

        String dirThumbnail = txTemplate.execute(status -> {
            dirFileRepository.deleteByEngineIdAndParentPath(engineId, path);

            String thumb = null;
            List<DirFile> toSave = new ArrayList<>();
            for (FileItem item : items) {
                DirFile df = new DirFile();
                df.setEngineId(engineId);
                df.setParentPath(path);
                df.setName(item.getName());
                df.setIsDir(item.getIsDir());
                df.setSize(item.getSize());
                df.setExt(item.getExt());
                df.setModifiedTime(item.getModified());
                toSave.add(df);

                if (!item.getIsDir() && thumb == null && isImageFile(item.getName())) {
                    thumb = "/api/files/proxy/" + engineId + "/" + encodePath(item.getPath());
                }
            }
            dirFileRepository.saveAll(toSave);
            return thumb;
        });

        for (FileItem item : items) {
            if (item.getIsDir()) {
                String subThumb = syncOneDir(engineId, item.getPath(), client);
                if (dirThumbnail == null && subThumb != null) {
                    dirThumbnail = subThumb;
                }
            }
        }

        if (dirThumbnail == null) {
            dirThumbnail = txTemplate.execute(status -> {
                String parentPath = parentOf(path);
                String dirName = nameOf(path);
                DirFile df = dirFileRepository.findByEngineIdAndParentPathAndName(engineId, parentPath, dirName);
                return df != null ? df.getThumbnailUrl() : null;
            });
        }

        return dirThumbnail;
    }

    private void updateThumbnail(Long engineId, String path, String thumbnail) {
        txTemplate.executeWithoutResult(status -> {
            String parentPath = parentOf(path);
            String dirName = nameOf(path);
            DirFile df = dirFileRepository.findByEngineIdAndParentPathAndName(engineId, parentPath, dirName);
            if (df != null) {
                df.setThumbnailUrl(thumbnail);
                dirFileRepository.save(df);
            }
        });
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
        return dirFileRepository.findByEngineIdAndParentPathOrderByIsDirDescNameAsc(engineId, path);
    }

    public List<DirFile> search(Long engineId, String parentPath, String keyword) {
        return dirFileRepository.searchByNameAndParentPath(engineId, parentPath, keyword);
    }

    public boolean hasData(Long engineId, String path) {
        return dirFileRepository.existsByEngineIdAndParentPath(engineId, path);
    }
}
