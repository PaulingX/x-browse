package com.xbrowse.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 路径标准化与 URL 编码工具
 * <p>
 * 统一处理 Alist / 本地缓存路径格式，以及中文路径的 URL 编解码。
 */
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * 标准化路径：保证以 / 开头，去除末尾多余 /
     */
    public static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 父路径
     */
    public static String parentOf(String path) {
        if ("/".equals(path)) {
            return "/";
        }
        String parent = path.substring(0, path.lastIndexOf('/'));
        return parent.isEmpty() ? "/" : parent;
    }

    /**
     * 路径最后一段名称
     */
    public static String nameOf(String path) {
        if ("/".equals(path)) {
            return "/";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 拼接父路径与文件名
     */
    public static String join(String parentPath, String name) {
        if (parentPath == null || parentPath.isEmpty() || "/".equals(parentPath)) {
            return "/" + name;
        }
        return parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;
    }

    /**
     * 编码路径用于 URL 路径段
     * <p>
     * 中文/空格安全：按段 UTF-8 编码，空格用 %20（不用 form 编码的 +）。
     */
    public static String encodeForUrl(String path) {
        String p = path != null && path.startsWith("/") ? path.substring(1) : path;
        if (p == null || p.isEmpty()) {
            return "";
        }
        String[] segments = p.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            // URLEncoder 面向 application/x-www-form-urlencoded，空格会变成 +
            // 路径段中空格必须是 %20，否则代理/流媒体中文路径可能失败
            sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    /**
     * 从 /api/files/{kind}/{engineId}/** 中解析文件路径
     * <p>
     * 支持中文 URL 编码；兼容容器已解码或仍为 %XX 的情况。
     */
    public static String extractFilePath(Long engineId, String uri, String kind) {
        String prefix = "/api/files/" + kind + "/" + engineId + "/";
        if (uri == null || !uri.startsWith(prefix)) {
            return "/";
        }
        String encoded = uri.substring(prefix.length());
        // 容器可能已解码，也可能仍是 %XX；统一按 UTF-8 解码
        String decoded = URLDecoder.decode(encoded.replace("+", "%20"), StandardCharsets.UTF_8);
        return decoded.startsWith("/") ? decoded : "/" + decoded;
    }
}
