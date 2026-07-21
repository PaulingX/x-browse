package com.xbrowse.repository;

import com.xbrowse.entity.DirFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirFileRepository extends JpaRepository<DirFile, Long> {

    List<DirFile> findByDirectoryId(Long directoryId);

    DirFile findByDirectoryIdAndName(Long directoryId, String name);

    @Query("SELECT d FROM DirFile d WHERE d.directoryId = :directoryId AND LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY d.name ASC")
    List<DirFile> searchByNameAndDirectoryId(@Param("directoryId") Long directoryId, @Param("keyword") String keyword);

    void deleteByDirectoryId(Long directoryId);

    void deleteByDirectoryIdIn(List<Long> directoryIds);

    void deleteByEngineId(Long engineId);
}
