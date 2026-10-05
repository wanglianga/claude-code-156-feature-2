package com.county.coldchain.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 复核人员重新抽样记录。 */
@Data
public class ReviewSampleRequest {
    @NotNull
    private BigDecimal sampleWeight;
    @NotNull
    private BigDecimal sampleBadWeight;
    /** 抽样复核人 */
    private String sampler;
    /** 抽样发现说明（坏果部位、成熟度、挤压/失水特征等） */
    private String sampleNote;
}
