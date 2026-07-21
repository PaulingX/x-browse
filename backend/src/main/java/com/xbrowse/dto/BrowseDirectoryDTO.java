package com.xbrowse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 浏览目录请求/响应 DTO
 */
public class BrowseDirectoryDTO {

    private Long id;

    @NotNull(message = "引擎 ID 不能为空")
    private Long engineId;

    @NotBlank(message = "目录路径不能为空")
    @Size(max = 1000, message = "路径不能超过1000个字符")
    private String path;

    @Size(max = 200, message = "名称不能超过200个字符")
    private String name;

    private Boolean thumbnailEnabled = true;

    /** 同步模式：NONE / INTERVAL / CRON */
    private String syncMode = "INTERVAL";

    /** 间隔数值 */
    private Integer syncIntervalValue = 5;

    /** 间隔单位：MINUTE / HOUR / DAY / MONTH */
    private String syncIntervalUnit = "MINUTE";

    /** Cron 表达式 */
    private String syncCron;

    private LocalDateTime lastSyncTime;
    private LocalDateTime nextSyncTime;

    /** 展示用：同步策略描述 */
    private String syncDesc;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEngineId() { return engineId; }
    public void setEngineId(Long engineId) { this.engineId = engineId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getThumbnailEnabled() { return thumbnailEnabled; }
    public void setThumbnailEnabled(Boolean thumbnailEnabled) { this.thumbnailEnabled = thumbnailEnabled; }
    public String getSyncMode() { return syncMode; }
    public void setSyncMode(String syncMode) { this.syncMode = syncMode; }
    public Integer getSyncIntervalValue() { return syncIntervalValue; }
    public void setSyncIntervalValue(Integer syncIntervalValue) { this.syncIntervalValue = syncIntervalValue; }
    public String getSyncIntervalUnit() { return syncIntervalUnit; }
    public void setSyncIntervalUnit(String syncIntervalUnit) { this.syncIntervalUnit = syncIntervalUnit; }
    public String getSyncCron() { return syncCron; }
    public void setSyncCron(String syncCron) { this.syncCron = syncCron; }
    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(LocalDateTime lastSyncTime) { this.lastSyncTime = lastSyncTime; }
    public LocalDateTime getNextSyncTime() { return nextSyncTime; }
    public void setNextSyncTime(LocalDateTime nextSyncTime) { this.nextSyncTime = nextSyncTime; }
    public String getSyncDesc() { return syncDesc; }
    public void setSyncDesc(String syncDesc) { this.syncDesc = syncDesc; }
}
