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
    List<FileDirectory> searchByNameAndParentId(@Param("engineId") Long engineId,
                                                @Param("parentId") Long parentId,
                                                @Param("keyword") String keyword);

    long countByEngineIdAndParentId(Long engineId, Long parentId);

    @Query(value = """
            SELECT * FROM file_directory WHERE engine_id = :engineId AND parent_id = :parentId
            ORDER BY
              CASE WHEN :sort = 'name_desc' THEN LOWER(name) END DESC,
              CASE WHEN :sort = 'name_asc' THEN LOWER(name) END ASC,
              CASE WHEN :sort = 'time_asc' THEN COALESCE(modified_time, 0) END ASC,
              CASE WHEN :sort = 'time_desc' THEN COALESCE(modified_time, 0) END DESC,
              LOWER(name) ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<FileDirectory> pageChildren(@Param("engineId") Long engineId,
                                     @Param("parentId") Long parentId,
                                     @Param("sort") String sort,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    /** parent_id IS NULL 的根层子目录 */
    @Query(value = """
            SELECT * FROM file_directory WHERE engine_id = :engineId AND parent_id IS NULL
            ORDER BY
              CASE WHEN :sort = 'name_desc' THEN LOWER(name) END DESC,
              CASE WHEN :sort = 'name_asc' THEN LOWER(name) END ASC,
              CASE WHEN :sort = 'time_asc' THEN COALESCE(modified_time, 0) END ASC,
              CASE WHEN :sort = 'time_desc' THEN COALESCE(modified_time, 0) END DESC,
              LOWER(name) ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<FileDirectory> pageRootChildren(@Param("engineId") Long engineId,
                                         @Param("sort") String sort,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    void deleteByEngineIdAndParentId(Long engineId, Long parentId);

    void deleteByEngineId(Long engineId);
}
