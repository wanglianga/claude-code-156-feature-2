package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.LossReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LossReviewRepository extends JpaRepository<LossReview, Long> {
    List<LossReview> findAllByOrderByCreatedAtDesc();
    List<LossReview> findByBatchIdOrderByCreatedAtDesc(Long batchId);
    List<LossReview> findBySortingIdOrderByCreatedAtDesc(Long sortingId);
    List<LossReview> findByStatusNotOrderByCreatedAtDesc(com.county.coldchain.domain.DomainEnums.ReviewStatus status);
    Optional<LossReview> findFirstBySortingIdAndStatusNot(Long sortingId,
                                                          com.county.coldchain.domain.DomainEnums.ReviewStatus status);
}
