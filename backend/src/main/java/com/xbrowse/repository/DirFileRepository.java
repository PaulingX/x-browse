package com.xbrowse.repository;

import com.xbrowse.entity.DirFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirFileRepository extends JpaRepository<DirFile, Long> {

    List<DirFile> findByEngineIdAndParentPathOrderByIsDirDescNameAsc(Long engineId, String parentPath);

    Page<DirFile> findByEngineIdAndParentPath(Long engineId, String parentPath, Pageable pageable);

    Page<DirFile> findByEngineIdAndParentPathOrderByIsDirDescNameAsc(Long engineId, String parentPath, Pageable pageable);

    Page<DirFile> findByDirectoryId(Long directoryId, Pageable pageable);

    List<DirFile> findByDirectoryIdOrderByNameAsc(Long directoryId);

    @Query("SELECT d FROM DirFile d WHERE d.engineId = :engineId AND d.parentPath = :parentPath AND LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY d.isDir DESC, d.name ASC")
    List<DirFile> searchByNameAndParentPath(@Param("engineId") Long engineId, @Param("parentPath") String parentPath, @Param("keyword") String keyword);

    @Query("SELECT d FROM DirFile d WHERE d.directoryId = :directoryId AND LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY d.name ASC")
    List<DirFile> searchByNameAndDirectoryId(@Param("directoryId") Long directoryId, @Param("keyword") String keyword);

    void deleteByEngineIdAndParentPath(Long engineId, String parentPath);

    void deleteByEngineIdAndParentPathStartingWith(Long engineId, String parentPathPrefix);

    void deleteByDirectoryId(Long directoryId);

    void deleteByEngineId(Long engineId);

    boolean existsByEngineIdAndParentPath(Long engineId, String parentPath);

    DirFile findByEngineIdAndParentPathAndName(Long engineId, String parentPath, String name);

    DirFile findByDirectoryIdAndName(Long directoryId, String name);
}
