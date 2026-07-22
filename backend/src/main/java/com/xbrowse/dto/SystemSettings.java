package com.xbrowse.dto;

/**
 * 系统设置 DTO
 */
public class SystemSettings {

    /** 全局缩略图生成开关 */
    private Boolean thumbnailEnabled;

    /** 数据目录路径（只读展示） */
    private String dataDir;

    /** 是否忽略以 . 开头的文件夹 */
    private Boolean ignoreDotDirs;

    /**
     * 忽略的文件后缀（逗号分隔，不含点）
     * 例：nfo,url,db,tmp
     */
    private String ignoreFileExtensions;

    public Boolean getThumbnailEnabled() {
        return thumbnailEnabled;
    }

    public void setThumbnailEnabled(Boolean thumbnailEnabled) {
        this.thumbnailEnabled = thumbnailEnabled;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Boolean getIgnoreDotDirs() {
        return ignoreDotDirs;
    }

    public void setIgnoreDotDirs(Boolean ignoreDotDirs) {
        this.ignoreDotDirs = ignoreDotDirs;
    }

    public String getIgnoreFileExtensions() {
        return ignoreFileExtensions;
    }

    public void setIgnoreFileExtensions(String ignoreFileExtensions) {
        this.ignoreFileExtensions = ignoreFileExtensions;
    }
}
