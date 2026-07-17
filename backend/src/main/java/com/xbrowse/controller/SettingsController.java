package com.xbrowse.controller;

import com.xbrowse.config.AppConfig;
import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.SystemSettings;
import com.xbrowse.service.CacheService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统设置控制器
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final AppConfig appConfig;
    private final CacheService cacheService;

    public SettingsController(AppConfig appConfig, CacheService cacheService) {
        this.appConfig = appConfig;
        this.cacheService = cacheService;
    }

    /**
     * 获取系统设置
     */
    @GetMapping
    public ApiResponse<SystemSettings> getSettings() {
        SystemSettings settings = new SystemSettings();
        settings.setThumbnailEnabled(appConfig.getThumbnailEnabled());
        settings.setCacheDir(appConfig.getCacheDir());
        settings.setDataDir(appConfig.getDataDir());
        return ApiResponse.success(settings);
    }

    /**
     * 更新系统设置（仅管理员）
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateSettings(@RequestBody SystemSettings settings) {
        if (settings.getThumbnailEnabled() != null) {
            appConfig.setThumbnailEnabled(settings.getThumbnailEnabled());
        }
        return ApiResponse.success("设置更新成功", null);
    }

    /**
     * 获取缓存信息
     */
    @GetMapping("/cache/info")
    public ApiResponse<Map<String, Object>> getCacheInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("cacheDir", appConfig.getCacheDir());
        info.put("cacheSize", cacheService.getCacheSize());
        info.put("thumbnailEnabled", appConfig.getThumbnailEnabled());
        return ApiResponse.success(info);
    }

    /**
     * 清空缓存（仅管理员）
     */
    @DeleteMapping("/cache")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> clearCache() {
        cacheService.clearCache();
        return ApiResponse.success("缓存已清空", null);
    }
}
