package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.FileItem;
import com.xbrowse.service.AlistEngineService;
import com.xbrowse.service.DirFileSyncService;
import com.xbrowse.service.FileBrowseService;
import com.xbrowse.service.ThumbnailCacheService;
import com.xbrowse.util.AlistClient;
import com.xbrowse.util.MediaTypes;
import com.xbrowse.util.PathUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件浏览控制器
 * <p>
 * 提供目录列表、搜索、同步、预览图、文件代理与视频流式播放接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    /** HTTP 206 Partial Content */
    private static final int PARTIAL_CONTENT = 206;

    private final FileBrowseService fileBrowseService;
    private final DirFileSyncService dirFileSyncService;
    private final AlistEngineService engineService;
    private final ThumbnailCacheService thumbnailCacheService;

    /** 浏览器缓存图片秒数（proxy / thumbnail） */
    @Value("${xbrowse.image-cache-seconds:86400}")
    private int imageCacheSeconds;

    /**
     * 批量获取目录预览图 URL
     */
    @GetMapping("/dir-thumbnail")
    public ApiResponse<Map<String, String>> getDirThumbnails(
            @RequestParam Long engineId,
            @RequestParam List<String> paths) {
        log.info("获取目录预览图: engineId={}, paths={}", engineId, paths.size());
        Map<String, String> result = new LinkedHashMap<>();
        for (String dirPath : paths) {
            result.put(dirPath, fileBrowseService.getDirThumbnail(engineId, dirPath));
        }
        return ApiResponse.success(result);
    }

    /**
     * 提供本地缓存的目录预览图
     * <p>
     * 从本地缓存目录读取缩略图文件，避免每次都代理请求 Alist。
     * 缓存文件由 DirFileSyncService 在同步时自动下载。
     */
    @GetMapping("/thumbnail/{engineId}/**")
    public ResponseEntity<Resource> serveCachedThumbnail(
            @PathVariable Long engineId,
            HttpServletRequest request) {
        // 从请求 URI 中提取缓存文件名
        String cacheFileName = PathUtils.extractFilePath(engineId, request.getRequestURI(), "thumbnail")
                .substring(1);
        log.debug("提供缓存预览图: engineId={}, cacheFileName={}", engineId, cacheFileName);

        Path cachePath = thumbnailCacheService.getCachedThumbnailPath(engineId, cacheFileName);
        if (cachePath == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Resource resource = new UrlResource(cachePath.toUri());
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(MediaTypes.contentType(cacheFileName)))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=" + imageCacheSeconds)
                    .body(resource);
        } catch (Exception e) {
            log.error("读取缓存预览图失败: engineId={}, cacheFileName={}", engineId, cacheFileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 搜索文件（数据库模糊搜索）
     */
    @GetMapping("/search")
    public ApiResponse<List<FileItem>> searchFiles(
            @RequestParam Long engineId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "/") String parentPath) {
        return ApiResponse.success(fileBrowseService.searchFiles(engineId, parentPath, keyword));
    }

    /**
     * 手动触发引擎下已配置浏览目录的同步
     */
    @PostMapping("/sync")
    public ApiResponse<String> triggerSync(@RequestParam Long engineId) {
        log.info("手动触发同步: engineId={}", engineId);
        dirFileSyncService.syncEngine(engineId);
        return ApiResponse.success("同步完成");
    }

    /**
     * 浏览目录
     *
     * @param refresh true 时直连 Alist；false 时优先读本地缓存
     */
    @GetMapping("/list")
    public ApiResponse<List<FileItem>> listFiles(
            @RequestParam Long engineId,
            @RequestParam(defaultValue = "/") String path,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestParam(defaultValue = "name_asc") String sort) {
        return ApiResponse.success(fileBrowseService.listFiles(engineId, path, refresh, page, perPage, sort));
    }

    /**
     * 代理获取文件（用于跨域或需要认证的图片等）
     * <p>
     * 不落盘原图，仅转发上游；带 Cache-Control 便于浏览器缓存。
     */
    @GetMapping("/proxy/{engineId}/**")
    public ResponseEntity<Resource> proxyFile(@PathVariable Long engineId, HttpServletRequest request) {
        try {
            String fullPath = PathUtils.extractFilePath(engineId, request.getRequestURI(), "proxy");
            String fileUrl = resolveFileUrl(engineId, fullPath);
            if (fileUrl == null) {
                return ResponseEntity.notFound().build();
            }
            return openUpstream(fileUrl, MediaTypes.contentType(fullPath), null,
                    Map.of(HttpHeaders.CACHE_CONTROL, "public, max-age=" + imageCacheSeconds));
        } catch (Exception e) {
            log.error("代理文件失败: engineId={}, path={}", engineId, request.getRequestURI(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取流媒体（用于视频播放，支持 Range 请求与拖动进度）
     */
    @GetMapping("/stream/{engineId}/**")
    public ResponseEntity<Resource> streamFile(
            @PathVariable Long engineId,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletRequest request) {
        try {
            String fullPath = PathUtils.extractFilePath(engineId, request.getRequestURI(), "stream");
            String fileUrl = resolveFileUrl(engineId, fullPath);
            if (fileUrl == null) {
                return ResponseEntity.notFound().build();
            }
            String contentType = MediaTypes.contentType(fullPath);

            // 无 Range：整文件返回，并声明支持字节范围
            if (range == null || range.isBlank()) {
                return openUpstream(fileUrl, contentType, null,
                        Map.of(HttpHeaders.ACCEPT_RANGES, "bytes"), 200, true);
            }

            long fileSize = headContentLength(fileUrl);
            // 无法获取大小时透传 Range，由上游处理
            if (fileSize <= 0) {
                return openUpstream(fileUrl, contentType, range,
                        Map.of(HttpHeaders.ACCEPT_RANGES, "bytes"), 206, true);
            }

            long[] bounds = parseByteRange(range, fileSize);
            if (bounds == null) {
                return ResponseEntity.status(416)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                        .build();
            }
            long start = bounds[0];
            long end = bounds[1];
            long contentLength = end - start + 1;
            Map<String, String> headers = Map.of(
                    HttpHeaders.ACCEPT_RANGES, "bytes",
                    HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize,
                    HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength)
            );
            return openUpstream(fileUrl, contentType, "bytes=" + start + "-" + end, headers, PARTIAL_CONTENT, true);
        } catch (Exception e) {
            log.error("流媒体播放失败: engineId={}, path={}", engineId, request.getRequestURI(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 解析 Range 请求头，支持 bytes=start-end / bytes=start- / bytes=-suffix
     *
     * @return [start, end]，非法时返回 null
     */
    private long[] parseByteRange(String range, long fileSize) {
        if (fileSize <= 0) {
            return null;
        }
        String value = range.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)) {
            return null;
        }
        // 不支持多段 Range
        value = value.substring(6).trim();
        if (value.contains(",")) {
            return null;
        }
        int dash = value.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startPart = value.substring(0, dash).trim();
        String endPart = value.substring(dash + 1).trim();
        long start;
        long end;
        try {
            if (startPart.isEmpty()) {
                // bytes=-N：最后 N 字节
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0, fileSize - suffix);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(startPart);
                end = endPart.isEmpty() ? fileSize - 1 : Long.parseLong(endPart);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (start < 0 || start >= fileSize) {
            return null;
        }
        end = Math.min(end, fileSize - 1);
        if (end < start) {
            return null;
        }
        return new long[]{start, end};
    }

    /**
     * 通过引擎客户端解析 Alist 原始文件地址
     */
    private String resolveFileUrl(Long engineId, String fullPath) {
        AlistClient client = engineService.getClient(engineId);
        return client.getFileUrl(fullPath);
    }

    /**
     * 打开上游 HTTP 资源并包装为响应（默认 200，普通代理超时）
     */
    private ResponseEntity<Resource> openUpstream(String fileUrl, String contentType,
                                                   String rangeHeader, Map<String, String> extraHeaders) throws Exception {
        return openUpstream(fileUrl, contentType, rangeHeader, extraHeaders, 200, false);
    }

    /**
     * 打开上游 HTTP 资源并包装为响应
     *
     * @param rangeHeader 可选 Range 请求头
     * @param status      HTTP 状态码（200 或 206）
     * @param streaming   是否视频流（使用更长读超时）
     */
    private ResponseEntity<Resource> openUpstream(String fileUrl, String contentType,
                                                   String rangeHeader, Map<String, String> extraHeaders,
                                                   int status, boolean streaming) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(MediaTypes.connectTimeoutMs());
        connection.setReadTimeout(streaming ? MediaTypes.streamReadTimeoutMs() : MediaTypes.readTimeoutMs());
        if (rangeHeader != null) {
            connection.setRequestProperty("Range", rangeHeader);
        }
        int upstreamStatus = connection.getResponseCode();
        if (upstreamStatus >= 400) {
            connection.disconnect();
            return ResponseEntity.status(upstreamStatus).build();
        }
        InputStream inputStream = connection.getInputStream();
        // 有 Range 时优先使用上游真实状态（200/206）
        int responseStatus = rangeHeader != null
                ? (upstreamStatus == PARTIAL_CONTENT ? PARTIAL_CONTENT : upstreamStatus)
                : status;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(responseStatus)
                .contentType(MediaType.parseMediaType(contentType));
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        // 上游带 Content-Range 时尽量透传，保证拖动进度正确
        String upstreamRange = connection.getHeaderField(HttpHeaders.CONTENT_RANGE);
        if (upstreamRange != null && !upstreamRange.isEmpty()
                && (extraHeaders == null || !extraHeaders.containsKey(HttpHeaders.CONTENT_RANGE))) {
            builder.header(HttpHeaders.CONTENT_RANGE, upstreamRange);
        }
        return builder.body(new InputStreamResource(inputStream));
    }

    /**
     * HEAD 请求获取上游文件大小，失败返回 -1
     */
    private long headContentLength(String fileUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(MediaTypes.headTimeoutMs());
            connection.setReadTimeout(MediaTypes.headTimeoutMs());
            long size = connection.getContentLengthLong();
            // 部分存储不支持 HEAD，回退 Content-Length 为 -1
            if (size <= 0) {
                String cl = connection.getHeaderField("Content-Length");
                if (cl != null && !cl.isEmpty()) {
                    size = Long.parseLong(cl);
                }
            }
            connection.disconnect();
            return size;
        } catch (Exception e) {
            return -1;
        }
    }
}
