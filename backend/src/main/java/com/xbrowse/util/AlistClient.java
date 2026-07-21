package com.xbrowse.util;

import com.xbrowse.dto.FileItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alist API 客户端
 * <p>
 * 封装 Alist 的用户校验、目录列表、文件直链获取。
 */
@Slf4j
public class AlistClient {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PER_PAGE = 1000;

    private final String url;
    private final String token;
    private final RestTemplate restTemplate;

    public AlistClient(String url, String token) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = token;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 获取当前用户信息（用于验证连接）
     */
    public String getCurrentUser() {
        Map<String, Object> data = dataMap(get("/api/me"));
        if (data != null && data.get("username") != null) {
            return String.valueOf(data.get("username"));
        }
        throw new RuntimeException("获取用户信息失败");
    }

    /**
     * 获取目录列表（默认分页参数）
     */
    public List<FileItem> listFiles(String path, boolean refresh) {
        return listFiles(path, refresh, DEFAULT_PAGE, DEFAULT_PER_PAGE);
    }

    /**
     * 获取目录列表（支持分页）
     *
     * @param path    Alist 路径，支持中文
     * @param refresh 是否强制刷新 Alist 缓存
     */
    @SuppressWarnings("unchecked")
    public List<FileItem> listFiles(String path, boolean refresh, int page, int perPage) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("path", path);
        body.put("refresh", refresh);
        body.put("page", page);
        body.put("per_page", perPage);

        Map<String, Object> data = dataMap(post("/api/fs/list", body));
        List<FileItem> items = new ArrayList<>();
        if (data == null) {
            return items;
        }
        Object contentObj = data.get("content");
        if (!(contentObj instanceof List<?> content)) {
            return items;
        }
        for (Object row : content) {
            if (!(row instanceof Map<?, ?> item)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) item;
            String name = (String) map.get("name");
            FileItem fileItem = new FileItem();
            fileItem.setName(name);
            fileItem.setIsDir(Boolean.TRUE.equals(map.get("is_dir")));
            fileItem.setSize(asLong(map.get("size"), 0L));
            fileItem.setModified(map.get("modified") != null ? parseTime(String.valueOf(map.get("modified"))) : null);
            fileItem.setPath(PathUtils.join(path, name));
            if (!Boolean.TRUE.equals(fileItem.getIsDir()) && name != null) {
                fileItem.setExt(MediaTypes.extensionOf(name));
            }
            items.add(fileItem);
        }
        return items;
    }

    /**
     * 获取文件的预览/下载直链
     */
    public String getFileUrl(String filePath) {
        Map<String, Object> body = new HashMap<>(1);
        body.put("path", filePath);
        Map<String, Object> data = dataMap(post("/api/fs/get", body));
        if (data == null) {
            return null;
        }
        String rawUrl = (String) data.get("raw_url");
        if (rawUrl != null && !rawUrl.isEmpty()) {
            return rawUrl;
        }
        return (String) data.get("url");
    }

    /**
     * 构建带 Authorization 的请求头
     */
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
        ResponseEntity<Map> resp = restTemplate.exchange(
                this.url + path, HttpMethod.POST, new HttpEntity<>(body, buildHeaders()), Map.class);
        log.debug("Alist POST {} -> status={}", path, resp.getStatusCode());
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        ResponseEntity<Map> resp = restTemplate.exchange(
                this.url + path, HttpMethod.GET, new HttpEntity<>(buildHeaders()), Map.class);
        return resp.getBody();
    }

    /**
     * 从 Alist 响应中取出 data 对象
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> dataMap(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object data = response.get("data");
        return data instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private long asLong(Object value, long defaultValue) {
        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    /**
     * 解析 Alist 返回的时间字符串为毫秒时间戳
     */
    private Long parseTime(String timeStr) {
        try {
            if (timeStr == null || timeStr.isEmpty()) {
                return null;
            }
            return OffsetDateTime.parse(timeStr).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.warn("解析时间失败: {}", timeStr);
            return null;
        }
    }
}
