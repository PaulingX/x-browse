package com.xbrowse.dto;

/**
 * 文件/目录项 DTO - 用于前端展示
 */
public class FileItem {

    private String name;
    private Boolean isDir;
    private Long size;
    private Long modified;
    /** 媒体访问地址（图片代理 / 视频流） */
    private String url;
    /** 列表预览图（如视频同名封面图） */
    private String thumbnailUrl;
    private String path;
    private String ext;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getIsDir() { return isDir; }
    public void setIsDir(Boolean isDir) { this.isDir = isDir; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public Long getModified() { return modified; }
    public void setModified(Long modified) { this.modified = modified; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }
}
