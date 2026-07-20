package com.xbrowse.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_directory", indexes = {
    @Index(name = "idx_filedir_engine_parent", columnList = "engine_id, parent_id"),
    @Index(name = "idx_filedir_engine_path", columnList = "engine_id, path", unique = true),
    @Index(name = "idx_filedir_name", columnList = "name")
})
public class FileDirectory {

    @Id
    @GeneratedValue(generator = "sqlite-id")
    @GenericGenerator(name = "sqlite-id", type = com.xbrowse.config.SQLiteIdGenerator.class)
    private Long id;

    @Column(name = "engine_id", nullable = false)
    private Long engineId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "modified_time")
    private Long modifiedTime;

    @Column(name = "sync_time")
    private LocalDateTime syncTime;

    @PrePersist
    protected void onCreate() {
        syncTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        syncTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEngineId() { return engineId; }
    public void setEngineId(Long engineId) { this.engineId = engineId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Long getModifiedTime() { return modifiedTime; }
    public void setModifiedTime(Long modifiedTime) { this.modifiedTime = modifiedTime; }
    public LocalDateTime getSyncTime() { return syncTime; }
    public void setSyncTime(LocalDateTime syncTime) { this.syncTime = syncTime; }
}
