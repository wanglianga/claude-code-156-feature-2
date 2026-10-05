package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.CrateEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrateEvidenceRepository extends JpaRepository<CrateEvidence, Long> {
    List<CrateEvidence> findByReviewIdOrderByBadFruitWeightDesc(Long reviewId);
    List<CrateEvidence> findByBatchId(Long batchId);
}
