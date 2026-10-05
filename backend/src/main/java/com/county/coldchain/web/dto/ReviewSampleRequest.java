package com.county.coldchain.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 复核重新抽样：可多次提交，按抽样坏果率推算全量损耗与可售重量。 */
@Data
public class ReviewSampleRequest {
    private String samplePoint;
    private BigDecimal sampleWeight;
    private BigDecimal rotWeight;
    private BigDecimal gradeAWeight;
    private BigDecimal gradeBWeight;
    private BigDecimal gradeProcessWeight;
    private String operator;
    private String remark;
}
