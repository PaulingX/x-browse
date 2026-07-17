package com.xbrowse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alist 引擎请求/响应 DTO
 */
public class AlistEngineDTO {

    private Long id;

    /**
     * 引擎备注名称
     */
    @Size(max = 100, message = "备注名称不能超过100个字符")
    private String remark;

    /**
     * Alist 服务地址
     */
    @NotBlank(message = "Alist 地址不能为空")
    @Size(max = 500, message = "地址不能超过500个字符")
    private String url;

    /**
     * 访问令牌
     */
    @NotBlank(message = "访问令牌不能为空")
    @Size(max = 500, message = "令牌不能超过500个字符")
    private String token;

    /**
     * 用户名（响应时返回）
     */
    private String userName;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
