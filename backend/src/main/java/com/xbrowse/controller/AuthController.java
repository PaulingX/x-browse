package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.LoginRequest;
import com.xbrowse.dto.UserDTO;
import com.xbrowse.entity.User;
import com.xbrowse.repository.UserRepository;
import com.xbrowse.security.JwtUtil;
import com.xbrowse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserRepository userRepository,
                          UserService userService,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            // 生成 Token
            String token = jwtUtil.generateToken(request.getUsername());

            // 从数据库获取用户实体
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            user.setLastLoginTime(java.time.LocalDateTime.now());
            userRepository.save(user);

            // 返回 Token 和用户信息
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("username", user.getUsername());
            data.put("displayName", user.getDisplayName());
            data.put("admin", user.getAdmin());

            return ApiResponse.success("登录成功", data);

        } catch (Exception e) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserDTO> getCurrentUser() {
        User user = userService.getCurrentUser();
        return ApiResponse.success(toDTO(user));
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> request) {
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return ApiResponse.error(400, "密码不能为空");
        }

        User user = userService.getCurrentUser();
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ApiResponse.error(400, "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ApiResponse.success("密码修改成功", null);
    }

    /**
     * 获取当前用户信息（公开方法，避免循环引用）
     */
    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setAdmin(user.getAdmin());
        dto.setEnabled(user.getEnabled());
        return dto;
    }
}
