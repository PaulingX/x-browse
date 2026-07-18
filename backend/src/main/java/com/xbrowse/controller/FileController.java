package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.AlistEngine;
import com.xbrowse.entity.DirFile;
import com.xbrowse.repository.AlistEngineRepository;
import com.xbrowse.service.DirFileSyncService;
import com.xbrowse.service.FileBrowseService;
import com.xbrowse.util.AlistClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文件浏览控制器
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileBrowseService fileBrowseService;
    private final DirFileSyncService dirFileSyncService;
    private final AlistEngineRepository engineRepository;

    public FileController(FileBrowseService fileBrowseService,
                          DirFileSyncService dirFileSyncService,
                          AlistEngineRepository engineRepository) {
        this.fileBrowseService = fileBrowseService;
        this.dirFileSyncService = dirFileSyncService;
        this.engineRepository = engineRepository;
    }

    /**
     * 获取目录预览图（第一个图片文件的代理 URL）
     */
    @GetMapping("/dir-thumbnail")
    public ApiResponse<Map<String, String>> getDirThumbnails(
            @RequestParam Long engineId,
            @RequestParam List<String> paths) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String dirPath : paths) {
            String thumb = fileBrowseService.getDirThumbnail(engineId, dirPath);
            result.put(dirPath, thumb);
        }
        return ApiResponse.success(result);
    }

    /**
     * 搜索文件（数据库模糊搜索）
     */
    @GetMapping("/search")
    public ApiResponse<List<FileItem>> searchFiles(
            @RequestParam Long engineId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "/") String parentPath) {
        List<DirFile> dirFiles = dirFileSyncService.search(engineId, parentPath, keyword);
        List<FileItem> items = new ArrayList<>();
        for (DirFile df : dirFiles) {
            FileItem fi = new FileItem();
            fi.setName(df.getName());
            fi.setIsDir(df.getIsDir());
            fi.setSize(df.getSize());
            fi.setExt(df.getExt());
            String fullPath = df.getParentPath().endsWith("/")
                    ? df.getParentPath() + df.getName()
                    : df.getParentPath() + "/" + df.getName();
            fi.setPath(fullPath);
            if (df.getIsDir()) {
                fi.setUrl(df.getThumbnailUrl());
            } else if (fileBrowseService.isImageFile(df.getName()) || fileBrowseService.isVideoFile(df.getName())) {
                fi.setUrl("/api/files/proxy/" + engineId + "/" + encodePath(fullPath));
            }
            items.add(fi);
        }
        return ApiResponse.success(items);
    }

    /**
     * 手动触发目录同步
     */
    @PostMapping("/sync")
    public ApiResponse<String> triggerSync(@RequestParam Long engineId) {
        dirFileSyncService.syncDirectory(engineId, "/");
        return ApiResponse.success("同步完成");
    }

    /**
     * 浏览目录
     */
    @GetMapping("/list")
    public ApiResponse<List<FileItem>> listFiles(
            @RequestParam Long engineId,
            @RequestParam(defaultValue = "/") String path,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        return ApiResponse.success(fileBrowseService.listFiles(engineId, path, refresh, page, perPage));
    }

    /**
     * 代理获取文件（用于跨域或需要认证的文件）
     */
    @GetMapping("/proxy/{engineId}/**")
    public ResponseEntity<Resource> proxyFile(
            @PathVariable Long engineId,
            HttpServletRequest request) {
        try {
            String fullPath = extractProxyPath(engineId, request.getRequestURI());
            String contentType = getContentType(fullPath);

            AlistEngine engine = getEngine(engineId);
            AlistClient client = new AlistClient(engine.getUrl(), engine.getToken());

            String fileUrl = client.getFileUrl(fullPath);
            if (fileUrl == null) {
                return ResponseEntity.notFound().build();
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));

            InputStream inputStream = connection.getInputStream();
            Resource resource = new org.springframework.core.io.InputStreamResource(inputStream);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取流媒体（用于视频播放）
     */
    @GetMapping("/stream/{engineId}/**")
    public ResponseEntity<Resource> streamFile(
            @PathVariable Long engineId,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletRequest request) {
        try {
            String fullPath = extractStreamPath(engineId, request.getRequestURI());

            AlistEngine engine = getEngine(engineId);
            AlistClient client = new AlistClient(engine.getUrl(), engine.getToken());
            String fileUrl = client.getFileUrl(fullPath);

            if (fileUrl == null) {
                return ResponseEntity.notFound().build();
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            if (range != null) {
                connection.setRequestProperty("Range", range);
            }
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            InputStream inputStream = connection.getInputStream();
            Resource resource = new org.springframework.core.io.InputStreamResource(inputStream);

            String contentType = getContentType(fullPath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private AlistEngine getEngine(Long engineId) {
        Optional<AlistEngine> engineOpt = engineRepository.findById(engineId);
        if (engineOpt.isEmpty()) {
            throw new RuntimeException("引擎不存在: " + engineId);
        }
        return engineOpt.get();
    }

    private String extractProxyPath(Long engineId, String uri) {
        String prefix = "/api/files/proxy/" + engineId + "/";
        if (uri.startsWith(prefix)) {
            String encoded = uri.substring(prefix.length());
            String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            return "/" + decoded;
        }
        return "/";
    }

    private String extractStreamPath(Long engineId, String uri) {
        String prefix = "/api/files/stream/" + engineId + "/";
        if (uri.startsWith(prefix)) {
            String encoded = uri.substring(prefix.length());
            String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            return "/" + decoded;
        }
        return "/";
    }

    private String getContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return "application/octet-stream";

        String ext = fileName.substring(dotIndex + 1).toLowerCase();

        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "ogg" -> "video/ogg";
            default -> "application/octet-stream";
        };
    }

    private String encodePath(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = p.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
