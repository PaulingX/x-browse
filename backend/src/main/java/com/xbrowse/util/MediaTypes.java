package com.xbrowse.util;

import java.util.Set;

/**
 * 媒体类型判断与媒体访问 URL 构建
 * <p>
 * 统一图片/视频扩展名判断，以及 proxy、stream 访问地址生成。
 */
public final class MediaTypes {

    /** 图片扩展名 */
    public static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"
    );

    /** 视频扩展名 */
    public static final Set<String> VIDEO_EXTS = Set.of(
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v"
    );

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    /** 视频流读取超时（长视频/拖动进度需要更长空闲容忍） */
    private static final int STREAM_READ_TIMEOUT_MS = 120_000;
    private static final int HEAD_TIMEOUT_MS = 10_000;

    private MediaTypes() {
    }

    /** HTTP 连接超时（毫秒） */
    public static int connectTimeoutMs() {
        return CONNECT_TIMEOUT_MS;
    }

    /** HTTP 读取超时（毫秒） */
    public static int readTimeoutMs() {
        return READ_TIMEOUT_MS;
    }

    /** 视频流读取超时（毫秒） */
    public static int streamReadTimeoutMs() {
        return STREAM_READ_TIMEOUT_MS;
    }

    /** HEAD 请求超时（毫秒） */
    public static int headTimeoutMs() {
        return HEAD_TIMEOUT_MS;
    }

    /**
     * 取文件扩展名（小写，不含点），无扩展名返回 null
     */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 是否为图片文件
     */
    public static boolean isImage(String fileName) {
        String ext = extensionOf(fileName);
        return ext != null && IMAGE_EXTS.contains(ext);
    }

    /**
     * 是否为视频文件
     */
    public static boolean isVideo(String fileName) {
        String ext = extensionOf(fileName);
        return ext != null && VIDEO_EXTS.contains(ext);
    }

    /**
     * 图片代理访问 URL（支持中文路径编码）
     */
    public static String proxyUrl(Long engineId, String path) {
        return "/api/files/proxy/" + engineId + "/" + PathUtils.encodeForUrl(path);
    }

    /**
     * 视频流式播放 URL（支持中文路径编码）
     */
    public static String streamUrl(Long engineId, String path) {
        return "/api/files/stream/" + engineId + "/" + PathUtils.encodeForUrl(path);
    }

    /**
     * 按文件类型填充预览/播放 URL（目录不处理）
     */
    public static String mediaUrl(Long engineId, String path, String fileName) {
        if (isVideo(fileName)) {
            return streamUrl(engineId, path);
        }
        if (isImage(fileName)) {
            return proxyUrl(engineId, path);
        }
        return null;
    }

    /**
     * 根据扩展名推断 Content-Type
     */
    public static String contentType(String fileName) {
        String ext = extensionOf(fileName);
        if (ext == null) {
            return "application/octet-stream";
        }
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "mp4", "m4v" -> "video/mp4";
            case "webm" -> "video/webm";
            case "ogg", "ogv" -> "video/ogg";
            case "mov" -> "video/quicktime";
            case "mkv" -> "video/x-matroska";
            case "avi" -> "video/x-msvideo";
            case "wmv" -> "video/x-ms-wmv";
            case "flv" -> "video/x-flv";
            default -> "application/octet-stream";
        };
    }
}
