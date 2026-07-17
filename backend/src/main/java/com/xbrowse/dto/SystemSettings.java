package com.xbrowse.dto;

/**
 * 系统设置 DTO
 */
public class SystemSettings {

    /**
     * 全局缩略图生成开关
     */
    private Boolean thumbnailEnabled;

    /**
     * 缓存目录路径
     */
    private String cacheDir;

    /**
     * 数据目录路径
     */
    private String dataDir;

    // Getters and Setters
    public Boolean getThumbnailEnabled() {
        return thumbnailEnabled;
    }

    public void setThumbnailEnabled(Boolean thumbnailEnabled) {
        this.thumbnailEnabled = thumbnailEnabled;
    }

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }
}
