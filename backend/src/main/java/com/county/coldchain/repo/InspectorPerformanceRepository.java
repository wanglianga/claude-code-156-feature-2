package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.InspectorPerformance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectorPerformanceRepository extends JpaRepository<InspectorPerformance, Long> {
    List<InspectorPerformance> findByInspectorOrderByOccurredAtDesc(String inspector);
    List<InspectorPerformance> findAllByOrderByOccurredAtDesc();
    List<InspectorPerformance> findByBatchId(Long batchId);
}
