package com.xbrowse.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;

/**
 * 浏览目录（同步根路径）
 * <p>
 * 每个浏览目录可独立配置同步策略：固定间隔或 Cron。
 */
@Entity
@Table(name = "browse_directory")
public class BrowseDirectory {

    /** 不同步 */
    public static final String SYNC_MODE_NONE = "NONE";
    /** 固定间隔 */
    public static final String SYNC_MODE_INTERVAL = "INTERVAL";
    /** Cron 表达式 */
    public static final String SYNC_MODE_CRON = "CRON";

    public static final String INTERVAL_MINUTE = "MINUTE";
    public static final String INTERVAL_HOUR = "HOUR";
    public static final String INTERVAL_DAY = "DAY";
    public static final String INTERVAL_MONTH = "MONTH";

    /** 综合：展示全部（除忽略文件） */
    public static final String MEDIA_TYPE_ALL = "all";
    /** 仅图片 */
    public static final String MEDIA_TYPE_IMAGE = "image";
    /** 仅视频（同名图片作封面后不单独列出） */
    public static final String MEDIA_TYPE_VIDEO = "video";

    @Id
    @GeneratedValue(generator = "sqlite-id")
    @GenericGenerator(name = "sqlite-id", type = com.xbrowse.config.SQLiteIdGenerator.class)
    private Long id;

    @Column(name = "engine_id", nullable = false)
    private Long engineId;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(length = 200)
    private String name;

    @Column(name = "thumbnail_enabled")
    private Boolean thumbnailEnabled = true;

    /**
     * 浏览类型：all 综合 / image 图片 / video 视频
     */
    @Column(name = "media_type", length = 20)
    private String mediaType = MEDIA_TYPE_ALL;

    /** 同步模式：NONE / INTERVAL / CRON */
    @Column(name = "sync_mode", length = 20)
    private String syncMode = SYNC_MODE_INTERVAL;

    /** 间隔数值（INTERVAL 模式） */
    @Column(name = "sync_interval_value")
    private Integer syncIntervalValue = 5;

    /** 间隔单位：MINUTE / HOUR / DAY / MONTH */
    @Column(name = "sync_interval_unit", length = 20)
    private String syncIntervalUnit = INTERVAL_MINUTE;

    /** Cron 表达式（CRON 模式，6 位：秒 分 时 日 月 周） */
    @Column(name = "sync_cron", length = 100)
    private String syncCron;

    /** 上次同步完成时间 */
    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    /** 下次计划同步时间（INTERVAL 模式） */
    @Column(name = "next_sync_time")
    private LocalDateTime nextSyncTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (syncMode == null || syncMode.isBlank()) {
            syncMode = SYNC_MODE_INTERVAL;
        }
        if (syncIntervalValue == null || syncIntervalValue <= 0) {
            syncIntervalValue = 5;
        }
        if (syncIntervalUnit == null || syncIntervalUnit.isBlank()) {
            syncIntervalUnit = INTERVAL_MINUTE;
        }
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = MEDIA_TYPE_ALL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

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
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
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
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
