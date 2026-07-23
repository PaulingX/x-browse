package com.xbrowse.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 媒体 URL 访问辅助：为 &lt;img&gt;/&lt;video&gt; 追加 JWT query token。
 */
@Service
public class MediaAccessService {

    public static final String REQUEST_TOKEN_ATTR = "xbrowse.jwtToken";

    /**
     * 为媒体 URL 追加 token 查询参数（若当前请求带 JWT）
     */
    public String withAccessToken(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        // 已是绝对外部地址则不处理
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String token = currentToken();
        if (token == null || token.isBlank()) {
            return url;
        }
        if (url.contains("token=")) {
            return url;
        }
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return url + (url.contains("?") ? "&" : "?") + "token=" + encoded;
    }

    public String currentToken() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            Object attr = request.getAttribute(REQUEST_TOKEN_ATTR);
            if (attr instanceof String s && !s.isBlank()) {
                return s;
            }
            String bearer = request.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                return bearer.substring(7).trim();
            }
            return request.getParameter("token");
        } catch (Exception e) {
            return null;
        }
    }
}
