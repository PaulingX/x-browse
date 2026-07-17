package com.xbrowse.util;

import com.xbrowse.dto.FileItem;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Alist API 客户端
 */
public class AlistClient {

    private final String url;
    private final String token;
    private final RestTemplate restTemplate;

    public AlistClient(String url, String token) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
        this.restTemplate = new RestTemplate();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isEmpty()) {
            headers.set("Authorization", token);
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        String fullUrl = this.url + path;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(fullUrl, HttpMethod.POST, entity, Map.class);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        String fullUrl = this.url + path;
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, Map.class);
        return resp.getBody();
    }

    /**
     * 获取当前用户信息（用于验证连接）
     */
    public String getCurrentUser() {
        Map<String, Object> response = get("/api/me");
        if (response != null && response.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data != null) {
                return (String) data.get("username");
            }
        }
        throw new RuntimeException("获取用户信息失败");
    }

    /**
     * 获取目录列表（向后兼容）
     */
    public List<FileItem> listFiles(String path, boolean refresh) {
        return listFiles(path, refresh, 1, 1000);
    }

    /**
     * 获取目录列表（支持分页）
     */
    public List<FileItem> listFiles(String path, boolean refresh, int page, int perPage) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("path", path);
        requestBody.put("refresh", refresh);
        requestBody.put("page", page);
        requestBody.put("per_page", perPage);

        Map<String, Object> response = post("/api/fs/list", requestBody);
        List<FileItem> items = new ArrayList<>();

        if (response != null && response.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data != null && data.containsKey("content")) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
                if (content != null) {
                    for (Map<String, Object> item : content) {
                        FileItem fileItem = new FileItem();
                        fileItem.setName((String) item.get("name"));
                        fileItem.setIsDir((Boolean) item.get("is_dir"));
                        fileItem.setSize(item.get("size") != null ? ((Number) item.get("size")).longValue() : 0L);
                        fileItem.setModified(item.get("modified") != null ? parseTime((String) item.get("modified")) : null);

                        String name = (String) item.get("name");
                        fileItem.setPath(path.endsWith("/") ? path + name : path + "/" + name);

                        if (!fileItem.getIsDir() && name != null) {
                            int dotIndex = name.lastIndexOf('.');
                            if (dotIndex > 0) {
                                fileItem.setExt(name.substring(dotIndex + 1).toLowerCase());
                            }
                        }

                        items.add(fileItem);
                    }
                }
            }
        }

        return items;
    }

    /**
     * 获取文件的预览/下载链接
     */
    public String getFileUrl(String filePath) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("path", filePath);

        Map<String, Object> response = post("/api/fs/get", requestBody);

        if (response != null && response.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data != null) {
                String rawUrl = (String) data.get("raw_url");
                if (rawUrl != null && !rawUrl.isEmpty()) {
                    return rawUrl;
                }
                return (String) data.get("url");
            }
        }
        return null;
    }

    private Long parseTime(String timeStr) {
        try {
            if (timeStr == null || timeStr.isEmpty()) {
                return null;
            }
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(
                    timeStr.replace(" ", "T"));
            return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }
}
