package com.xbrowse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 应用配置类
 */
@Configuration
public class AppConfig {

    /**
     * 数据存储目录
     */
    @Value("${xbrowse.data-dir:./data}")
    private String dataDir;

    /**
     * 缩略图开关
     */
    @Value("${xbrowse.thumbnail-enabled:true}")
    private Boolean thumbnailEnabled;

    /**
     * JWT 密钥
     */
    @Value("${xbrowse.jwt-secret}")
    private String jwtSecret;

    /**
     * JWT 过期时间（秒）
     */
    @Value("${xbrowse.jwt-expiration:604800}")
    private Long jwtExpiration;

    // Getters
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
}
