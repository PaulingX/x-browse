package com.xbrowse.dto;

/**
 * 文件/目录项 DTO - 用于前端展示
 */
public class FileItem {

    private String name;
    private Boolean isDir;
    private Long size;
    private Long modified;
    private String url;
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
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getExt() { return ext; }
    public void setExt(String ext) { this.ext = ext; }
}
