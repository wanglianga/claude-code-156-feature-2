package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.CustomerCompensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCompensationRepository extends JpaRepository<CustomerCompensation, Long> {
    List<CustomerCompensation> findByReviewIdOrderByCreatedAtDesc(Long reviewId);
    List<CustomerCompensation> findAllByOrderByCreatedAtDesc();
    List<CustomerCompensation> findByBatchIdOrderByCreatedAtDesc(Long batchId);
}
