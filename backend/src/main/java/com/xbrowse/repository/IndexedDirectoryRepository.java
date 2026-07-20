package com.xbrowse.repository;

import com.xbrowse.entity.IndexedDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexedDirectoryRepository extends JpaRepository<IndexedDirectory, Long> {

    Optional<IndexedDirectory> findByEngineIdAndPath(Long engineId, String path);

    List<IndexedDirectory> findByEngineIdAndParentIdOrderByNameAsc(Long engineId, Long parentId);

    List<IndexedDirectory> findByBrowseDirectoryId(Long browseDirectoryId);

    void deleteByEngineId(Long engineId);

    void deleteByEngineIdAndPathStartingWith(Long engineId, String pathPrefix);
}
