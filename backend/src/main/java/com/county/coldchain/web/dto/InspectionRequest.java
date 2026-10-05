package com.county.coldchain.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InspectionRequest {
    @NotNull
    private Long batchId;
    private String inspector;
    private Integer appearanceScore;
    private Integer maturityScore;
    private BigDecimal rotRate;
    private BigDecimal moistureRate;
    private BigDecimal measuredTemp;
    private Boolean certificateOk;
    private Boolean mixedConfirmed;
    /** ACCEPT / SORT / DOWNGRADE / RETURN */
    @NotNull
    private String decision;
    /** A / B / PROCESS（ACCEPT 时建议等级；DOWNGRADE 时降级等级） */
    private String suggestedGrade;
    private BigDecimal downgradeWeight;
    private BigDecimal returnWeight;
    private BigDecimal estimatedLossRate;
    /** 质检员电子签名（签名图片 URL/编号或手写签名文本） */
    private String signature;
    private String remark;
}
