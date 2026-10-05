package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.CustomerCompensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCompensationRepository extends JpaRepository<CustomerCompensation, Long> {
    List<CustomerCompensation> findByBatchIdOrderByCreatedAtDesc(Long batchId);
    List<CustomerCompensation> findByReviewId(Long reviewId);
    List<CustomerCompensation> findAllByOrderByCreatedAtDesc();
}
