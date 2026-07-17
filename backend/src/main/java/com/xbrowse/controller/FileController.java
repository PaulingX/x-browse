package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.AlistEngine;
import com.xbrowse.repository.AlistEngineRepository;
import com.xbrowse.service.CacheService;
import com.xbrowse.service.FileBrowseService;
import com.xbrowse.util.AlistClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * 文件浏览控制器
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileBrowseService fileBrowseService;
    private final CacheService cacheService;
    private final AlistEngineRepository engineRepository;

    public FileController(FileBrowseService fileBrowseService,
                          CacheService cacheService,
                          AlistEngineRepository engineRepository) {
        this.fileBrowseService = fileBrowseService;
        this.cacheService = cacheService;
        this.engineRepository = engineRepository;
    }

    /**
     * 浏览目录
     *
     * @param engineId 引擎 ID
     * @param path     目录路径
     * @param refresh  是否刷新缓存
     * @param page     页码（从1开始）
     * @param perPage  每页数量（默认20）
     * @return 文件列表
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
    @GetMapping("/proxy/{type}/{engineId}/**")
    public ResponseEntity<Resource> proxyFile(
            @PathVariable String type,
            @PathVariable Long engineId,
            HttpServletRequest request) {
        try {
            String fullPath = extractPathFromUrl(type, engineId, request.getRequestURI());

            AlistEngine engine = getEngine(engineId);
            AlistClient client = new AlistClient(engine.getUrl(), engine.getToken());

            String fileUrl = client.getFileUrl(fullPath);
            if (fileUrl == null) {
                return ResponseEntity.notFound().build();
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            connection.setRequestMethod("GET");
            String contentType = getContentType(fullPath);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));

            String fileName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
            headers.setContentDispositionFormData("inline", fileName);

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
     * 获取本地缓存文件
     */
    @GetMapping("/cache/{type}/{engineId}/**")
    public ResponseEntity<Resource> getCacheFile(
            @PathVariable String type,
            @PathVariable Long engineId,
            HttpServletRequest request) {
        try {
            String fullPath = extractPathFromUrl(type, engineId, request.getRequestURI());
            String localPath = fileBrowseService.getLocalCachePath(engineId, fullPath);

            if (localPath == null) {
                return ResponseEntity.notFound().build();
            }

            File file = new File(localPath);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String contentType = getContentType(localPath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
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
            String localPath = fileBrowseService.getLocalCachePath(engineId, fullPath);

            if (localPath != null) {
                File file = new File(localPath);
                return streamLocalFile(file, range);
            } else {
                AlistEngine engine = getEngine(engineId);
                AlistClient client = new AlistClient(engine.getUrl(), engine.getToken());
                String fileUrl = client.getFileUrl(fullPath);
                return streamRemoteFile(fileUrl, range, fullPath);
            }

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

    private ResponseEntity<Resource> streamLocalFile(File file, String range) {
        Resource resource = new FileSystemResource(file);
        String contentType = getContentType(file.getName());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }

    private ResponseEntity<Resource> streamRemoteFile(String fileUrl, String range, String fileName) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            if (range != null) {
                connection.setRequestProperty("Range", range);
            }
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            InputStream inputStream = connection.getInputStream();
            Resource resource = new org.springframework.core.io.InputStreamResource(inputStream);

            String contentType = getContentType(fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractPathFromUrl(String type, Long engineId, String uri) {
        String prefix = "/api/files/proxy/" + type + "/" + engineId + "/";
        if (uri.startsWith(prefix)) {
            String encoded = uri.substring(prefix.length());
            // 反转 encodePath: 直接还原为 /path
            return "/" + encoded;
        }
        return "/";
    }

    private String extractStreamPath(Long engineId, String uri) {
        String prefix = "/api/files/stream/" + engineId + "/";
        if (uri.startsWith(prefix)) {
            String encoded = uri.substring(prefix.length());
            return "/" + encoded;
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
}
