package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.GmxPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GmxPositionRepository extends JpaRepository<GmxPosition, Long> {

    Optional<GmxPosition> findByChainNameAndPositionKey(String chainName, String positionKey);

    List<GmxPosition> findByChainNameAndAccountOrderByLastUpdateBlockDesc(String chainName, String account);

    List<GmxPosition> findByChainNameAndStatusOrderByLastUpdateBlockDesc(String chainName, GmxPosition.Status status);

    long countByChainName(String chainName);

    void deleteByChainName(String chainName);
}