package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.BrowseDirectoryDTO;
import com.xbrowse.service.BrowseDirectoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 浏览目录管理控制器
 */
@RestController
@RequestMapping("/api/directories")
public class DirectoryController {

    private final BrowseDirectoryService directoryService;

    public DirectoryController(BrowseDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    /**
     * 获取所有目录列表
     */
    @GetMapping
    public ApiResponse<List<BrowseDirectoryDTO>> listDirectories() {
        return ApiResponse.success(directoryService.listDirectories());
    }

    /**
     * 根据引擎 ID 获取目录列表
     */
    @GetMapping("/engine/{engineId}")
    public ApiResponse<List<BrowseDirectoryDTO>> listByEngine(@PathVariable Long engineId) {
        return ApiResponse.success(directoryService.listByEngineId(engineId));
    }

    /**
     * 获取启用缓存的目录列表
     */
    @GetMapping("/cache-enabled")
    public ApiResponse<List<BrowseDirectoryDTO>> listCacheEnabled() {
        return ApiResponse.success(directoryService.listCacheEnabled());
    }

    /**
     * 添加目录（仅管理员）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BrowseDirectoryDTO> addDirectory(@Valid @RequestBody BrowseDirectoryDTO dto) {
        return ApiResponse.success("目录添加成功", directoryService.addDirectory(dto));
    }

    /**
     * 更新目录（仅管理员）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BrowseDirectoryDTO> updateDirectory(@PathVariable Long id,
                                                            @Valid @RequestBody BrowseDirectoryDTO dto) {
        return ApiResponse.success("目录更新成功", directoryService.updateDirectory(id, dto));
    }

    /**
     * 删除目录（仅管理员）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteDirectory(@PathVariable Long id) {
        directoryService.deleteDirectory(id);
        return ApiResponse.success("目录删除成功", null);
    }
}
