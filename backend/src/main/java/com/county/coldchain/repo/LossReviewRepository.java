package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.LossReview;
import com.county.coldchain.domain.DomainEnums.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LossReviewRepository extends JpaRepository<LossReview, Long> {
    List<LossReview> findByBatchIdOrderByCreatedAtDesc(Long batchId);
    Optional<LossReview> findBySortingId(Long sortingId);
    List<LossReview> findByStatusInOrderByCreatedAtDesc(List<ReviewStatus> statuses);
    List<LossReview> findAllByOrderByCreatedAtDesc();
}
