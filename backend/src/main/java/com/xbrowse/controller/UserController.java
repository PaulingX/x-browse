package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.UserDTO;
import com.xbrowse.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取所有用户列表（仅管理员）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserDTO>> listUsers() {
        return ApiResponse.success(userService.listUsers());
    }

    /**
     * 根据 ID 获取用户（仅管理员）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDTO> getUser(@PathVariable Long id) {
        return ApiResponse.success(userService.getUser(id));
    }

    /**
     * 创建用户（仅管理员）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDTO> createUser(@RequestBody Map<String, Object> request) {
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String displayName = (String) request.get("displayName");
        Boolean admin = (Boolean) request.get("admin");

        return ApiResponse.success("用户创建成功",
                userService.createUser(username, password, displayName, admin));
    }

    /**
     * 更新用户（仅管理员）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDTO> updateUser(@PathVariable Long id,
                                            @RequestBody Map<String, Object> request) {
        String displayName = (String) request.get("displayName");
        Boolean enabled = (Boolean) request.get("enabled");

        return ApiResponse.success("用户更新成功",
                userService.updateUser(id, displayName, enabled));
    }

    /**
     * 删除用户（仅管理员）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success("用户删除成功", null);
    }

    /**
     * 重置用户密码（仅管理员）
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                            @RequestBody Map<String, String> request) {
        String newPassword = request.get("newPassword");
        userService.resetPassword(id, newPassword);
        return ApiResponse.success("密码重置成功", null);
    }

    /**
     * 更新用户目录权限（仅管理员）
     */
    @PutMapping("/{id}/directories")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateUserDirectories(@PathVariable Long id,
                                                    @RequestBody List<Long> directoryIds) {
        userService.updateUserDirectories(id, directoryIds);
        return ApiResponse.success("目录权限更新成功", null);
    }

    /**
     * 获取用户目录权限
     */
    @GetMapping("/{id}/directories")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Long>> getUserDirectories(@PathVariable Long id) {
        return ApiResponse.success(userService.getUserDirectoryIds(id));
    }
}
