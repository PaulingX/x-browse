package com.xbrowse.repository;

import com.xbrowse.entity.UserDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户目录权限数据访问层
 */
@Repository
public interface UserDirectoryRepository extends JpaRepository<UserDirectory, Long> {

    /**
     * 查询用户有权限的目录 ID 列表
     */
    @Query("SELECT ud.directoryId FROM UserDirectory ud WHERE ud.userId = :userId")
    List<Long> findDirectoryIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询目录被分配给了哪些用户
     */
    List<UserDirectory> findByDirectoryId(Long directoryId);

    /**
     * 检查用户是否有某目录的权限
     */
    boolean existsByUserIdAndDirectoryId(Long userId, Long directoryId);

    /**
     * 删除用户的所有目录权限
     */
    void deleteByUserId(Long userId);

    /**
     * 删除用户的目录权限
     */
    void deleteByUserIdAndDirectoryId(Long userId, Long directoryId);

    /**
     * 删除目录的所有权限分配
     */
    void deleteByDirectoryId(Long directoryId);
}
