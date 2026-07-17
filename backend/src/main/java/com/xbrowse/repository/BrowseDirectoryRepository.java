package com.xbrowse.repository;

import com.xbrowse.entity.BrowseDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 浏览目录数据访问层
 */
@Repository
public interface BrowseDirectoryRepository extends JpaRepository<BrowseDirectory, Long> {

    /**
     * 根据引擎 ID 查询目录列表
     */
    List<BrowseDirectory> findByEngineId(Long engineId);

    /**
     * 查询启用缓存的目录
     */
    List<BrowseDirectory> findByCacheEnabledTrue();

    /**
     * 检查目录是否已存在
     */
    boolean existsByEngineIdAndPath(Long engineId, String path);
}
