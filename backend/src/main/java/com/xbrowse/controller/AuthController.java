package com.xbrowse.controller;

import com.xbrowse.dto.ApiResponse;
import com.xbrowse.dto.LoginRequest;
import com.xbrowse.dto.PasswordChangeRequest;
import com.xbrowse.dto.UserDTO;
import com.xbrowse.entity.User;
import com.xbrowse.repository.UserRepository;
import com.xbrowse.security.JwtUtil;
import com.xbrowse.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * <p>
 * 负责登录、当前用户信息、修改密码。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * 用户登录
     * <p>
     * 认证成功后签发 JWT，并更新最后登录时间。
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 校验用户名密码
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            // 生成 Token
            String token = jwtUtil.generateToken(request.getUsername());
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            user.setLastLoginTime(LocalDateTime.now());
            userRepository.save(user);

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
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserDTO> getCurrentUser() {
        return ApiResponse.success(userService.toCurrentUserDTO(userService.getCurrentUser()));
    }

    /**
     * 修改当前用户密码
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(request);
        return ApiResponse.success("密码修改成功", null);
    }
}
