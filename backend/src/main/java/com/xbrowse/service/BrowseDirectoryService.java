package com.xbrowse.service;

import com.xbrowse.dto.BrowseDirectoryDTO;
import com.xbrowse.entity.BrowseDirectory;
import com.xbrowse.repository.BrowseDirectoryRepository;
import com.xbrowse.repository.UserDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 浏览目录管理服务
 * <p>
 * 管理管理员配置的浏览根路径；添加后会触发该路径下的目录同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseDirectoryService {

    private final BrowseDirectoryRepository directoryRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final DirFileSyncService dirFileSyncService;

    /**
     * 获取所有浏览目录列表
     */
    public List<BrowseDirectoryDTO> listDirectories() {
        return directoryRepository.findAll().stream().map(this::toDTO).toList();
    }

    /**
     * 根据引擎 ID 获取浏览目录列表
     */
    public List<BrowseDirectoryDTO> listByEngineId(Long engineId) {
        return directoryRepository.findByEngineId(engineId).stream().map(this::toDTO).toList();
    }

    /**
     * 添加浏览目录，保存后同步该路径下的 file_directory / dir_file
     */
    public BrowseDirectoryDTO addDirectory(BrowseDirectoryDTO dto) {
        if (directoryRepository.existsByEngineIdAndPath(dto.getEngineId(), dto.getPath())) {
            throw new RuntimeException("该目录已存在");
        }
        BrowseDirectory directory = new BrowseDirectory();
        directory.setEngineId(dto.getEngineId());
        directory.setPath(dto.getPath());
        directory.setName(dto.getName());
        directory.setThumbnailEnabled(dto.getThumbnailEnabled());
        directory = directoryRepository.save(directory);

        BrowseDirectoryDTO result = toDTO(directory);
        try {
            log.info("浏览目录已添加，开始同步: engineId={}, path={}", directory.getEngineId(), directory.getPath());
            dirFileSyncService.syncDirectory(directory.getEngineId(), directory.getPath());
        } catch (Exception e) {
            log.error("添加浏览目录后同步失败: engineId={}, path={}", directory.getEngineId(), directory.getPath(), e);
        }
        return result;
    }

    /**
     * 更新浏览目录显示名与缩略图开关
     */
    @Transactional
    public BrowseDirectoryDTO updateDirectory(Long id, BrowseDirectoryDTO dto) {
        BrowseDirectory directory = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));
        directory.setName(dto.getName());
        directory.setThumbnailEnabled(dto.getThumbnailEnabled());
        return toDTO(directoryRepository.save(directory));
    }

    /**
     * 删除浏览目录，并清理用户权限关联
     */
    @Transactional
    public void deleteDirectory(Long id) {
        userDirectoryRepository.deleteByDirectoryId(id);
        directoryRepository.deleteById(id);
    }

    /**
     * 实体转 DTO
     */
    private BrowseDirectoryDTO toDTO(BrowseDirectory directory) {
        BrowseDirectoryDTO dto = new BrowseDirectoryDTO();
        dto.setId(directory.getId());
        dto.setEngineId(directory.getEngineId());
        dto.setPath(directory.getPath());
        dto.setName(directory.getName());
        dto.setThumbnailEnabled(directory.getThumbnailEnabled());
        return dto;
    }
}
