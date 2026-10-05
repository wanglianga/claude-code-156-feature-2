package com.county.coldchain.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 货主发起分拣损耗争议复核 */
@Data
public class ReviewOpenRequest {
    private Long sortingId;
    private Long batchId;
    /** 货主主张的合理损耗率上限（0-1） */
    private BigDecimal ownerClaimLossRate;
    private String ownerClaimNote;
    private String reviewer;
}
