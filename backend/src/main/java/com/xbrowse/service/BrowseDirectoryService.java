package com.xbrowse.service;

import com.xbrowse.dto.BrowseDirectoryDTO;
import com.xbrowse.entity.BrowseDirectory;
import com.xbrowse.repository.BrowseDirectoryRepository;
import com.xbrowse.repository.UserDirectoryRepository;
import com.xbrowse.util.SyncScheduleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 浏览目录管理服务
 * <p>
 * 管理管理员配置的浏览根路径及各自独立的同步计划。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseDirectoryService {

    private final BrowseDirectoryRepository directoryRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final DirFileSyncService dirFileSyncService;

    public List<BrowseDirectoryDTO> listDirectories() {
        return directoryRepository.findAll().stream().map(this::toDTO).toList();
    }

    public List<BrowseDirectoryDTO> listByEngineId(Long engineId) {
        return directoryRepository.findByEngineId(engineId).stream().map(this::toDTO).toList();
    }

    /**
     * 添加浏览目录：保存配置后异步同步，接口立即返回
     */
    public BrowseDirectoryDTO addDirectory(BrowseDirectoryDTO dto) {
        if (directoryRepository.existsByEngineIdAndPath(dto.getEngineId(), dto.getPath())) {
            throw new RuntimeException("该目录已存在");
        }
        BrowseDirectory directory = new BrowseDirectory();
        applyDto(directory, dto, true);
        SyncScheduleUtils.normalize(directory);
        // 首次添加：标记可调度；同步在后台执行，避免保存按钮一直转圈
        if (!BrowseDirectory.SYNC_MODE_NONE.equals(directory.getSyncMode())) {
            directory.setNextSyncTime(LocalDateTime.now());
        }
        directory = directoryRepository.save(directory);

        // 异步同步，不阻塞 HTTP
        if (!BrowseDirectory.SYNC_MODE_NONE.equals(directory.getSyncMode())) {
            dirFileSyncService.syncBrowseDirectoryAfterSave(directory.getId());
        }
        return toDTO(directory);
    }

    /**
     * 更新浏览目录（含同步计划）
     */
    @Transactional
    public BrowseDirectoryDTO updateDirectory(Long id, BrowseDirectoryDTO dto) {
        BrowseDirectory directory = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));
        applyDto(directory, dto, false);
        SyncScheduleUtils.normalize(directory);

        LocalDateTime base = directory.getLastSyncTime() != null
                ? directory.getLastSyncTime()
                : LocalDateTime.now();
        if (BrowseDirectory.SYNC_MODE_NONE.equals(directory.getSyncMode())) {
            directory.setNextSyncTime(null);
        } else {
            directory.setNextSyncTime(SyncScheduleUtils.calcNextSyncTime(directory, base));
        }
        return toDTO(directoryRepository.save(directory));
    }

    @Transactional
    public void deleteDirectory(Long id) {
        userDirectoryRepository.deleteByDirectoryId(id);
        directoryRepository.deleteById(id);
    }

    /**
     * 手动触发同步（后台执行，立即返回当前目录信息）
     */
    public BrowseDirectoryDTO syncNow(Long id) {
        BrowseDirectory directory = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));
        dirFileSyncService.syncBrowseDirectoryAsync(id);
        return toDTO(directory);
    }

    private void applyDto(BrowseDirectory directory, BrowseDirectoryDTO dto, boolean creating) {
        if (creating) {
            directory.setEngineId(dto.getEngineId());
            directory.setPath(dto.getPath());
        }
        directory.setName(dto.getName());
        directory.setThumbnailEnabled(dto.getThumbnailEnabled() == null || dto.getThumbnailEnabled());
        directory.setMediaType(normalizeMediaType(dto.getMediaType()));
        directory.setSyncMode(dto.getSyncMode());
        directory.setSyncIntervalValue(dto.getSyncIntervalValue());
        directory.setSyncIntervalUnit(dto.getSyncIntervalUnit());
        directory.setSyncCron(dto.getSyncCron());
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return BrowseDirectory.MEDIA_TYPE_ALL;
        }
        String t = mediaType.trim().toLowerCase();
        if (BrowseDirectory.MEDIA_TYPE_IMAGE.equals(t) || BrowseDirectory.MEDIA_TYPE_VIDEO.equals(t)) {
            return t;
        }
        return BrowseDirectory.MEDIA_TYPE_ALL;
    }

    private BrowseDirectoryDTO toDTO(BrowseDirectory directory) {
        BrowseDirectoryDTO dto = new BrowseDirectoryDTO();
        dto.setId(directory.getId());
        dto.setEngineId(directory.getEngineId());
        dto.setPath(directory.getPath());
        dto.setName(directory.getName());
        dto.setThumbnailEnabled(directory.getThumbnailEnabled());
        dto.setMediaType(normalizeMediaType(directory.getMediaType()));
        dto.setSyncMode(directory.getSyncMode() != null ? directory.getSyncMode() : BrowseDirectory.SYNC_MODE_INTERVAL);
        dto.setSyncIntervalValue(directory.getSyncIntervalValue() != null ? directory.getSyncIntervalValue() : 5);
        dto.setSyncIntervalUnit(directory.getSyncIntervalUnit() != null
                ? directory.getSyncIntervalUnit() : BrowseDirectory.INTERVAL_MINUTE);
        dto.setSyncCron(directory.getSyncCron());
        dto.setLastSyncTime(directory.getLastSyncTime());
        dto.setNextSyncTime(directory.getNextSyncTime());
        dto.setSyncDesc(SyncScheduleUtils.describe(directory));
        return dto;
    }
}
