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

    long countByDirectoryId(Long directoryId);

    @Query(value = "SELECT COUNT(*) FROM dir_file WHERE directory_id = :directoryId AND LOWER(ext) IN (:exts)", nativeQuery = true)
    long countByDirectoryIdAndExtIn(@Param("directoryId") Long directoryId, @Param("exts") List<String> exts);

    /** SQL 分页：全部文件 */
    @Query(value = """
            SELECT * FROM dir_file WHERE directory_id = :directoryId
            ORDER BY
              CASE WHEN :sort = 'name_desc' THEN LOWER(name) END DESC,
              CASE WHEN :sort = 'name_asc' THEN LOWER(name) END ASC,
              CASE WHEN :sort = 'time_asc' THEN COALESCE(modified_time, 0) END ASC,
              CASE WHEN :sort = 'time_desc' THEN COALESCE(modified_time, 0) END DESC,
              LOWER(name) ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DirFile> pageByDirectoryId(@Param("directoryId") Long directoryId,
                                    @Param("sort") String sort,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    /** SQL 分页：按扩展名过滤（图片/视频） */
    @Query(value = """
            SELECT * FROM dir_file WHERE directory_id = :directoryId AND LOWER(ext) IN (:exts)
            ORDER BY
              CASE WHEN :sort = 'name_desc' THEN LOWER(name) END DESC,
              CASE WHEN :sort = 'name_asc' THEN LOWER(name) END ASC,
              CASE WHEN :sort = 'time_asc' THEN COALESCE(modified_time, 0) END ASC,
              CASE WHEN :sort = 'time_desc' THEN COALESCE(modified_time, 0) END DESC,
              LOWER(name) ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<DirFile> pageByDirectoryIdAndExtIn(@Param("directoryId") Long directoryId,
                                            @Param("exts") List<String> exts,
                                            @Param("sort") String sort,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    @Query("SELECT d FROM DirFile d WHERE d.directoryId = :directoryId AND LOWER(d.ext) IN :exts")
    List<DirFile> findImageFilesByDirectoryId(@Param("directoryId") Long directoryId,
                                              @Param("exts") List<String> exts);

    void deleteByDirectoryId(Long directoryId);

    void deleteByDirectoryIdIn(List<Long> directoryIds);

    void deleteByEngineId(Long engineId);
}
