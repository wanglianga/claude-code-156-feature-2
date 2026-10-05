package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.ReviewSample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewSampleRepository extends JpaRepository<ReviewSample, Long> {
    List<ReviewSample> findByReviewIdOrderBySampleSeqAsc(Long reviewId);
    long countByReviewId(Long reviewId);
}
