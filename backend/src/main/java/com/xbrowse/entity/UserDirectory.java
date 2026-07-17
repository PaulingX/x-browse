package com.xbrowse.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_directory")
public class UserDirectory {

    @Id
    @GeneratedValue(generator = "sqlite-id")
    @GenericGenerator(name = "sqlite-id", type = com.xbrowse.config.SQLiteIdGenerator.class)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "directory_id", nullable = false)
    private Long directoryId;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDirectoryId() { return directoryId; }
    public void setDirectoryId(Long directoryId) { this.directoryId = directoryId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
