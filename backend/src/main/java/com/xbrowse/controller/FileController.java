package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.FileItem;
import com.xbrowse.entity.AlistEngine;
import com.xbrowse.repository.AlistEngineRepository;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 文件浏览控制器
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileBrowseService fileBrowseService;
    private final AlistEngineRepository engineRepository;

    public FileController(FileBrowseService fileBrowseService,
                          AlistEngineRepository engineRepository) {
        this.fileBrowseService = fileBrowseService;
        this.engineRepository = engineRepository;
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
}
