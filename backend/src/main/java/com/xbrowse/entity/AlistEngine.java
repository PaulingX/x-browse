package com.xbrowse.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.time.LocalDateTime;

@Entity
@Table(name = "alist_engine")
public class AlistEngine {

    @Id
    @GeneratedValue(generator = "sqlite-id")
    @GenericGenerator(name = "sqlite-id", type = com.xbrowse.config.SQLiteIdGenerator.class)
    private Long id;

    @Column(length = 100)
    private String remark;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String userName;

    @Column(nullable = false, length = 500)
    private String token;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
