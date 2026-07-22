package com.xbrowse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用配置类（部分项可运行时修改）
 */
@Configuration
public class AppConfig {

    @Value("${xbrowse.data-dir:./data}")
    private String dataDir;

    @Value("${xbrowse.thumbnail-enabled:true}")
    private Boolean thumbnailEnabled;

    @Value("${xbrowse.jwt-secret}")
    private String jwtSecret;

    @Value("${xbrowse.jwt-expiration:604800}")
    private Long jwtExpiration;

    /**
     * 是否忽略以 . 开头的文件夹（不同步入库）
     */
    @Value("${xbrowse.ignore-dot-dirs:true}")
    private Boolean ignoreDotDirs;

    /**
     * 忽略的文件后缀（逗号分隔，不含点），如 nfo,url,db
     */
    @Value("${xbrowse.ignore-file-extensions:nfo,url,db,tmp,log,ini}")
    private String ignoreFileExtensions;

    public String getDataDir() {
        return dataDir;
    }

    public Boolean getThumbnailEnabled() {
        return thumbnailEnabled;
    }

    public void setThumbnailEnabled(Boolean thumbnailEnabled) {
        this.thumbnailEnabled = thumbnailEnabled;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public Long getJwtExpiration() {
        return jwtExpiration;
    }

    public Boolean getIgnoreDotDirs() {
        return ignoreDotDirs == null || ignoreDotDirs;
    }

    public void setIgnoreDotDirs(Boolean ignoreDotDirs) {
        this.ignoreDotDirs = ignoreDotDirs == null || ignoreDotDirs;
    }

    public String getIgnoreFileExtensions() {
        return ignoreFileExtensions == null ? "" : ignoreFileExtensions;
    }

    public void setIgnoreFileExtensions(String ignoreFileExtensions) {
        this.ignoreFileExtensions = ignoreFileExtensions == null ? "" : ignoreFileExtensions.trim();
    }

    /**
     * 解析后的忽略后缀集合（小写、无点）
     */
    public Set<String> getIgnoreFileExtensionSet() {
        String raw = getIgnoreFileExtensions();
        if (raw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith(".") ? s.substring(1) : s)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 是否应忽略该目录名（点开头）
     */
    public boolean shouldIgnoreDirName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return getIgnoreDotDirs() && name.startsWith(".");
    }

    /**
     * 是否应忽略该文件（按后缀）
     */
    public boolean shouldIgnoreFileName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        Set<String> ignores = getIgnoreFileExtensionSet();
        if (ignores.isEmpty()) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ignores.contains(ext);
    }
}
