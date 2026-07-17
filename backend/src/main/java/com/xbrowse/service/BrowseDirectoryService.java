package com.xbrowse.service;

import com.xbrowse.dto.BrowseDirectoryDTO;
import com.xbrowse.entity.BrowseDirectory;
import com.xbrowse.repository.BrowseDirectoryRepository;
import com.xbrowse.repository.UserDirectoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 浏览目录管理服务
 */
@Service
public class BrowseDirectoryService {

    private final BrowseDirectoryRepository directoryRepository;
    private final UserDirectoryRepository userDirectoryRepository;

    public BrowseDirectoryService(BrowseDirectoryRepository directoryRepository,
                                  UserDirectoryRepository userDirectoryRepository) {
        this.directoryRepository = directoryRepository;
        this.userDirectoryRepository = userDirectoryRepository;
    }

    /**
     * 获取所有目录列表
     */
    public List<BrowseDirectoryDTO> listDirectories() {
        return directoryRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据引擎 ID 获取目录列表
     */
    public List<BrowseDirectoryDTO> listByEngineId(Long engineId) {
        return directoryRepository.findByEngineId(engineId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取启用缓存的目录列表
     */
    public List<BrowseDirectoryDTO> listCacheEnabled() {
        return directoryRepository.findByCacheEnabledTrue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 添加目录
     */
    @Transactional
    public BrowseDirectoryDTO addDirectory(BrowseDirectoryDTO dto) {
        // 检查是否已存在
        if (directoryRepository.existsByEngineIdAndPath(dto.getEngineId(), dto.getPath())) {
            throw new RuntimeException("该目录已存在");
        }

        BrowseDirectory directory = new BrowseDirectory();
        directory.setEngineId(dto.getEngineId());
        directory.setPath(dto.getPath());
        directory.setName(dto.getName());
        directory.setCacheEnabled(dto.getCacheEnabled());
        directory.setThumbnailEnabled(dto.getThumbnailEnabled());

        directory = directoryRepository.save(directory);
        return toDTO(directory);
    }

    /**
     * 更新目录
     */
    @Transactional
    public BrowseDirectoryDTO updateDirectory(Long id, BrowseDirectoryDTO dto) {
        BrowseDirectory directory = directoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("目录不存在"));

        directory.setName(dto.getName());
        directory.setCacheEnabled(dto.getCacheEnabled());
        directory.setThumbnailEnabled(dto.getThumbnailEnabled());

        directory = directoryRepository.save(directory);
        return toDTO(directory);
    }

    /**
     * 删除目录
     */
    @Transactional
    public void deleteDirectory(Long id) {
        // 删除相关权限
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
        dto.setCacheEnabled(directory.getCacheEnabled());
        dto.setThumbnailEnabled(directory.getThumbnailEnabled());
        return dto;
    }
}
