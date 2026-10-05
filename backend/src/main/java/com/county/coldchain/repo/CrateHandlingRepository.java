package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.CrateHandlingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrateHandlingRepository extends JpaRepository<CrateHandlingRecord, Long> {
    List<CrateHandlingRecord> findByBatchIdOrderByArrivedAtAsc(Long batchId);
    List<CrateHandlingRecord> findBySortingIdOrderByArrivedAtAsc(Long sortingId);
    Optional<CrateHandlingRecord> findFirstByBatchIdAndCrateNoOrderByArrivedAtAsc(Long batchId, String crateNo);
}
