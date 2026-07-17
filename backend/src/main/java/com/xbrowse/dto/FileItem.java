package com.xbrowse.dto;

/**
 * 文件/目录项 DTO - 用于前端展示
 */
public class FileItem {

    /**
     * 文件/目录名称
     */
    private String name;

    /**
     * 是否为目录
     */
    private Boolean isDir;

    /**
     * 文件大小（字节），目录为 0
     */
    private Long size;

    /**
     * 修改时间（时间戳毫秒）
     */
    private Long modified;

    /**
     * 缩略图 URL（仅图片/视频）
     */
    private String thumbnail;

    /**
     * 预览 URL（用于大图/视频播放）
     */
    private String url;

    /**
     * 完整路径
     */
    private String path;

    /**
     * 文件扩展名
     */
    private String ext;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getIsDir() {
        return isDir;
    }

    public void setIsDir(Boolean isDir) {
        this.isDir = isDir;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getModified() {
        return modified;
    }

    public void setModified(Long modified) {
        this.modified = modified;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }
}
