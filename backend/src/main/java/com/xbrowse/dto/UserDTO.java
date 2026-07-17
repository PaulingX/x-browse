package com.xbrowse.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户响应 DTO
 */
public class UserDTO {

    private Long id;
    private String username;
    private String displayName;
    private Boolean admin;
    private Boolean enabled;
    private LocalDateTime lastLoginTime;
    private List<Long> directoryIds;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getAdmin() {
        return admin;
    }

    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public List<Long> getDirectoryIds() {
        return directoryIds;
    }

    public void setDirectoryIds(List<Long> directoryIds) {
        this.directoryIds = directoryIds;
    }
}
