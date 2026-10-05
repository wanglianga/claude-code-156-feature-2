package com.county.coldchain.repo;

import com.county.coldchain.domain.entity.ReviewBasketClue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewBasketClueRepository extends JpaRepository<ReviewBasketClue, Long> {
    List<ReviewBasketClue> findByReviewIdOrderBySortStationAtAsc(Long reviewId);
}
