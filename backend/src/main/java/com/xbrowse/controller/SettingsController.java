package com.xbrowse.controller;

import com.xbrowse.config.AppConfig;
import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.SystemSettings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统设置控制器
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final AppConfig appConfig;

    public SettingsController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * 获取系统设置
     */
    @GetMapping
    public ApiResponse<SystemSettings> getSettings() {
        SystemSettings settings = new SystemSettings();
        settings.setThumbnailEnabled(appConfig.getThumbnailEnabled());
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
}
