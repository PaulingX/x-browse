package com.xbrowse.repository;

import com.xbrowse.entity.FileDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileDirectoryRepository extends JpaRepository<FileDirectory, Long> {

    Optional<FileDirectory> findByEngineIdAndPath(Long engineId, String path);

    List<FileDirectory> findByEngineIdAndParentId(Long engineId, Long parentId);

    List<FileDirectory> findByEngineIdAndPathStartingWith(Long engineId, String pathPrefix);

    @Query("SELECT d FROM FileDirectory d WHERE d.engineId = :engineId AND d.parentId = :parentId AND LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<FileDirectory> searchByNameAndParentId(@Param("engineId") Long engineId, @Param("parentId") Long parentId, @Param("keyword") String keyword);

    void deleteByEngineIdAndParentId(Long engineId, Long parentId);


    void deleteByEngineId(Long engineId);
}
