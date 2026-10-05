package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.SortingAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SortingAssessmentRepository extends JpaRepository<SortingAssessment, Long> {
    List<SortingAssessment> findByReviewIdOrderByIdAsc(Long reviewId);
    List<SortingAssessment> findAllByOrderByCreatedAtDesc();
    List<SortingAssessment> findByBatchIdOrderByCreatedAtDesc(Long batchId);
}
