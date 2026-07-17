package com.xbrowse.repository;

import com.xbrowse.entity.AlistEngine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Alist 引擎数据访问层
 */
@Repository
public interface AlistEngineRepository extends JpaRepository<AlistEngine, Long> {
}
