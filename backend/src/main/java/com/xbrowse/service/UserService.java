package com.xbrowse.service;

import com.xbrowse.dto.PasswordChangeRequest;
import com.xbrowse.dto.UserDTO;
import com.xbrowse.entity.User;
import com.xbrowse.repository.UserRepository;
import com.xbrowse.repository.UserDirectoryRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserDirectoryRepository userDirectoryRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userDirectoryRepository = userDirectoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 获取当前登录用户
     */
    public User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * 获取所有用户列表
     */
    public List<UserDTO> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取用户
     */
    public UserDTO getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toDTO(user);
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
        user.setAdmin(admin != null && admin);
        user.setEnabled(true);

        user = userRepository.save(user);
        return toDTO(user);
    }

    /**
     * 更新用户
     */
    @Transactional
    public UserDTO updateUser(Long id, String displayName, Boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setDisplayName(displayName);
        user.setEnabled(enabled);

        user = userRepository.save(user);
        return toDTO(user);
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        User user = getCurrentUser();

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * 管理员重置用户密码
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 更新用户目录权限
     */
    @Transactional
    public void updateUserDirectories(Long userId, List<Long> directoryIds) {
        // 先删除原有权限
        userDirectoryRepository.deleteByUserId(userId);

        // 添加新权限
        for (Long directoryId : directoryIds) {
            com.xbrowse.entity.UserDirectory ud = new com.xbrowse.entity.UserDirectory();
            ud.setUserId(userId);
            ud.setDirectoryId(directoryId);
            userDirectoryRepository.save(ud);
        }
    }

    /**
     * 获取用户有权限的目录 ID 列表
     */
    public List<Long> getUserDirectoryIds(Long userId) {
        return userDirectoryRepository.findDirectoryIdsByUserId(userId);
    }

    /**
     * 验证用户是否有目录权限
     */
    public boolean hasDirectoryPermission(Long userId, Long directoryId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getAdmin()) {
            return true; // 管理员有所有权限
        }
        return userDirectoryRepository.existsByUserIdAndDirectoryId(userId, directoryId);
    }

    /**
     * 实体转 DTO
     */
    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setAdmin(user.getAdmin());
        dto.setEnabled(user.getEnabled());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setDirectoryIds(getUserDirectoryIds(user.getId()));
        return dto;
    }
}
