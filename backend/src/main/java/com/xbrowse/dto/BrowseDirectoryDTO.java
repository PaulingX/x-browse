package com.xbrowse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 浏览目录请求/响应 DTO
 */
public class BrowseDirectoryDTO {

    private Long id;

    /**
     * 关联的 Alist 引擎 ID
     */
    @NotNull(message = "引擎 ID 不能为空")
    private Long engineId;

    /**
     * 目录路径
     */
    @NotBlank(message = "目录路径不能为空")
    @Size(max = 1000, message = "路径不能超过1000个字符")
    private String path;

    /**
     * 目录显示名称
     */
    @Size(max = 200, message = "名称不能超过200个字符")
    private String name;

    /**
     * 是否启用本地缓存
     */
    private Boolean cacheEnabled = false;

    /**
     * 是否自动生成缩略图
     */
    private Boolean thumbnailEnabled = true;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEngineId() {
        return engineId;
    }

    public void setEngineId(Long engineId) {
        this.engineId = engineId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(Boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Boolean getThumbnailEnabled() {
        return thumbnailEnabled;
    }

    public void setThumbnailEnabled(Boolean thumbnailEnabled) {
        this.thumbnailEnabled = thumbnailEnabled;
    }
}
