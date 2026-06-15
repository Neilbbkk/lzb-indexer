package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.SyncError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncErrorRepository extends JpaRepository<SyncError, Long> {
}