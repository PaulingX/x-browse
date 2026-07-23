package com.xbrowse.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;

@Entity
@Table(name = "dir_file", indexes = {
    @Index(name = "idx_dirfile_directory", columnList = "directory_id"),
    @Index(name = "idx_dirfile_directory_name", columnList = "directory_id, name"),
    @Index(name = "idx_dirfile_directory_ext", columnList = "directory_id, ext"),
    @Index(name = "idx_dirfile_directory_mtime", columnList = "directory_id, modified_time"),
    @Index(name = "idx_dirfile_name", columnList = "name"),
    @Index(name = "idx_dirfile_engine", columnList = "engine_id")
})
public class DirFile {

    @Id
    @GeneratedValue(generator = "sqlite-id")
    @GenericGenerator(name = "sqlite-id", type = com.xbrowse.config.SQLiteIdGenerator.class)
    private Long id;

    @Column(name = "engine_id", nullable = false)
    private Long engineId;

    @Column(name = "directory_id")
    private Long directoryId;

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

    /** 视频封面：同目录同名图片的代理 URL（同步时写入） */
    @Column(name = "cover_url", length = 1000)
    private String coverUrl;

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
    public Long getDirectoryId() { return directoryId; }
    public void setDirectoryId(Long directoryId) { this.directoryId = directoryId; }
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
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Long getModifiedTime() { return modifiedTime; }
    public void setModifiedTime(Long modifiedTime) { this.modifiedTime = modifiedTime; }
    public LocalDateTime getSyncTime() { return syncTime; }
    public void setSyncTime(LocalDateTime syncTime) { this.syncTime = syncTime; }
}
