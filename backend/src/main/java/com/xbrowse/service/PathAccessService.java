package com.xbrowse.service;

import com.xbrowse.entity.BrowseDirectory;
import com.xbrowse.entity.User;
import com.xbrowse.repository.BrowseDirectoryRepository;
import com.xbrowse.repository.UserDirectoryRepository;
import com.xbrowse.util.PathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路径级访问控制：普通用户仅可访问被授权的浏览目录及其子路径。
 */
@Service
@RequiredArgsConstructor
public class PathAccessService {

    private final UserService userService;
    private final BrowseDirectoryRepository browseDirectoryRepository;
    private final UserDirectoryRepository userDirectoryRepository;

    /**
     * 校验当前用户是否可访问该引擎下的路径；不通过则抛出 403。
     */
    public void assertPathAllowed(Long engineId, String path) {
        if (!isPathAllowed(engineId, path)) {
            throw new AccessDeniedException("无权访问该路径");
        }
    }

    /**
     * 路径是否在当前用户授权范围内
     */
    public boolean isPathAllowed(Long engineId, String path) {
        if (engineId == null) {
            return false;
        }
        User user = userService.getCurrentUserOrNull();
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return false;
        }
        // 管理员可访问任意路径（含未配置浏览根，便于管理端选目录）
        if (Boolean.TRUE.equals(user.getAdmin())) {
            return true;
        }
        String target = PathUtils.normalize(path);
        List<BrowseDirectory> roots = resolveAllowedRoots(user.getId(), engineId);
        for (BrowseDirectory root : roots) {
            if (isUnderRoot(target, root.getPath())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前用户在某引擎下被授权的浏览根路径（已标准化）
     */
    public List<String> listAllowedRootPaths(Long engineId) {
        User user = userService.getCurrentUserOrNull();
        if (user == null) {
            return List.of();
        }
        if (Boolean.TRUE.equals(user.getAdmin())) {
            return browseDirectoryRepository.findByEngineId(engineId).stream()
                    .map(d -> PathUtils.normalize(d.getPath()))
                    .toList();
        }
        return resolveAllowedRoots(user.getId(), engineId).stream()
                .map(d -> PathUtils.normalize(d.getPath()))
                .toList();
    }

    private List<BrowseDirectory> resolveAllowedRoots(Long userId, Long engineId) {
        List<Long> dirIds = userDirectoryRepository.findDirectoryIdsByUserId(userId);
        if (dirIds == null || dirIds.isEmpty()) {
            return List.of();
        }
        Set<Long> idSet = new HashSet<>(dirIds);
        List<BrowseDirectory> result = new ArrayList<>();
        for (BrowseDirectory d : browseDirectoryRepository.findByEngineId(engineId)) {
            if (idSet.contains(d.getId())) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * target 是否等于 root，或为 root 的子路径
     */
    public static boolean isUnderRoot(String targetPath, String rootPath) {
        String target = PathUtils.normalize(targetPath);
        String root = PathUtils.normalize(rootPath);
        if ("/".equals(root)) {
            return true;
        }
        return target.equals(root) || target.startsWith(root + "/");
    }
}
