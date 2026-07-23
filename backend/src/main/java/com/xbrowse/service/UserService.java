package com.xbrowse.service;

import com.xbrowse.dto.PasswordChangeRequest;
import com.xbrowse.dto.UserDTO;
import com.xbrowse.entity.User;
import com.xbrowse.entity.UserDirectory;
import com.xbrowse.repository.UserDirectoryRepository;
import com.xbrowse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理服务
 * <p>
 * 负责用户 CRUD、改密、浏览目录权限（user_directory）维护。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取当前登录用户实体
     */
    public User getCurrentUser() {
        User user = getCurrentUserOrNull();
        if (user == null) {
            throw new RuntimeException("未登录");
        }
        return user;
    }

    /**
     * 当前登录用户，未登录返回 null
     */
    public User getCurrentUserOrNull() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof User user) {
                return user;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取所有用户列表（含 directoryIds）
     */
    public List<UserDTO> listUsers() {
        return userRepository.findAll().stream().map(this::toDTO).toList();
    }

    /**
     * 根据 ID 获取用户
     */
    public UserDTO getUser(Long id) {
        return toDTO(userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在")));
    }

    /**
     * 创建用户
     */
    @Transactional
    public UserDTO createUser(String username, String password, String displayName, Boolean admin) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setAdmin(Boolean.TRUE.equals(admin));
        user.setEnabled(true);
        return toDTO(userRepository.save(user));
    }

    /**
     * 更新用户显示名与启用状态
     */
    @Transactional
    public UserDTO updateUser(Long id, String displayName, Boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setDisplayName(displayName);
        user.setEnabled(enabled);
        return toDTO(userRepository.save(user));
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * 当前用户修改密码（需校验原密码）
     */
    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        User user = getCurrentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * 管理员重置指定用户密码
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 更新用户可访问的浏览目录权限（全量替换）
     */
    @Transactional
    public void updateUserDirectories(Long userId, List<Long> directoryIds) {
        userDirectoryRepository.deleteByUserId(userId);
        for (Long directoryId : directoryIds) {
            UserDirectory ud = new UserDirectory();
            ud.setUserId(userId);
            ud.setDirectoryId(directoryId);
            userDirectoryRepository.save(ud);
        }
    }

    /**
     * 获取用户有权限的浏览目录 ID 列表
     */
    public List<Long> getUserDirectoryIds(Long userId) {
        return userDirectoryRepository.findDirectoryIdsByUserId(userId);
    }

    /**
     * 当前用户信息（含 directoryIds，供前端展示可访问目录）
     */
    public UserDTO toCurrentUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setAdmin(user.getAdmin());
        dto.setEnabled(user.getEnabled());
        dto.setDirectoryIds(getUserDirectoryIds(user.getId()));
        return dto;
    }

    /**
     * 管理端用户 DTO（额外含最后登录时间）
     */
    private UserDTO toDTO(User user) {
        UserDTO dto = toCurrentUserDTO(user);
        dto.setLastLoginTime(user.getLastLoginTime());
        return dto;
    }

    /**
     * 当前用户是否管理员
     */
    public boolean isCurrentUserAdmin() {
        return Boolean.TRUE.equals(getCurrentUser().getAdmin());
    }

    /**
     * 当前用户被授权的浏览目录 ID（管理员不依赖此列表）
     */
    public List<Long> getCurrentUserDirectoryIds() {
        return getUserDirectoryIds(getCurrentUser().getId());
    }
}
