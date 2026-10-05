package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 复核重新抽样记录：一次抽样的重量、坏果重量与等级复核口径。 */
@Getter
@Setter
@Entity
@Table(name = "biz_review_sample")
public class ReviewSample extends BaseEntity {
    private Long reviewId;
    /** 抽样序号（同一复核单可多次抽样） */
    private Integer sampleSeq;
    /** 抽样筐号/取样点（可空） */
    private String samplePoint;
    private BigDecimal sampleWeight;
    private BigDecimal rotWeight;
    /** 抽样时复核的 A/B/加工 等级重量，三个字段之和 = sampleWeight - rotWeight */
    private BigDecimal gradeAWeight = BigDecimal.ZERO;
    private BigDecimal gradeBWeight = BigDecimal.ZERO;
    private BigDecimal gradeProcessWeight = BigDecimal.ZERO;
    private String operator;
    private LocalDateTime sampledAt;
    @Column(columnDefinition = "text")
    private String remark;
}
