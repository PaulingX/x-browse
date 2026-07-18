package com.xbrowse.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;

@Entity
@Table(name = "dir_file", indexes = {
    @Index(name = "idx_dirfile_engine_parent", columnList = "engine_id, parent_path"),
    @Index(name = "idx_dirfile_name", columnList = "name")
})
public class DirFile {

    @Id
    @GeneratedValue(generator = "sqlite-id")
    @GenericGenerator(name = "sqlite-id", type = com.xbrowse.config.SQLiteIdGenerator.class)
    private Long id;

    @Column(name = "engine_id", nullable = false)
    private Long engineId;

    @Column(name = "parent_path", nullable = false, length = 1000)
    private String parentPath;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "is_dir", nullable = false)
    private Boolean isDir;

    @Column(name = "file_size")
    private Long size;

    @Column(length = 20)
    private String ext;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEngineId() { return engineId; }
    public void setEngineId(Long engineId) { this.engineId = engineId; }
    public String getParentPath() { return parentPath; }
    public void setParentPath(String parentPath) { this.parentPath = parentPath; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getIsDir() { return isDir; }
    public void setIsDir(Boolean isDir) { this.isDir = isDir; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Long getModifiedTime() { return modifiedTime; }
    public void setModifiedTime(Long modifiedTime) { this.modifiedTime = modifiedTime; }
    public LocalDateTime getSyncTime() { return syncTime; }
    public void setSyncTime(LocalDateTime syncTime) { this.syncTime = syncTime; }
}
