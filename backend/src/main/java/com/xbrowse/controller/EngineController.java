package com.xbrowse.controller;

import com.xbrowse.dto.AlistEngineDTO;
import com.xbrowse.dto.ApiResponse;
import com.xbrowse.service.AlistEngineService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Alist 引擎管理控制器
 */
@RestController
@RequestMapping("/api/engines")
public class EngineController {

    private final AlistEngineService engineService;

    public EngineController(AlistEngineService engineService) {
        this.engineService = engineService;
    }

    /**
     * 获取所有引擎列表
     */
    @GetMapping
    public ApiResponse<List<AlistEngineDTO>> listEngines() {
        return ApiResponse.success(engineService.listEngines());
    }

    /**
     * 根据 ID 获取引擎
     */
    @GetMapping("/{id}")
    public ApiResponse<AlistEngineDTO> getEngine(@PathVariable Long id) {
        return ApiResponse.success(engineService.getEngine(id));
    }

    /**
     * 添加引擎（仅管理员）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AlistEngineDTO> addEngine(@Valid @RequestBody AlistEngineDTO dto) {
        return ApiResponse.success("引擎添加成功", engineService.addEngine(dto));
    }

    /**
     * 更新引擎（仅管理员）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AlistEngineDTO> updateEngine(@PathVariable Long id,
                                                     @Valid @RequestBody AlistEngineDTO dto) {
        return ApiResponse.success("引擎更新成功", engineService.updateEngine(id, dto));
    }

    /**
     * 删除引擎（仅管理员）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteEngine(@PathVariable Long id) {
        engineService.deleteEngine(id);
        return ApiResponse.success("引擎删除成功", null);
    }

    /**
     * 测试引擎连接
     */
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Object> testConnection(@Valid @RequestBody AlistEngineDTO dto) {
        String error = engineService.testConnection(dto);
        if (error == null) {
            return ApiResponse.success("连接成功", true);
        }
        return ApiResponse.success(error, false);
    }
}
