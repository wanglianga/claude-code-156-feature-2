package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.AssessmentResult;
import com.county.coldchain.domain.DomainEnums.AssessmentTarget;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 损耗复核考核记录：复核结论回写质检员考核 / 分拣班组考核 / 装卸班组考核。
 * 同一复核单可同时产生多条（如装卸挤压问责装卸班组、分拣暴露问责分拣班组、
 * 到货质量差且质检未拦截时问责质检员）。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_sorting_assessment")
public class SortingAssessment extends BaseEntity {
    private Long reviewId;
    private Long batchId;
    @Enumerated(EnumType.STRING)
    private AssessmentTarget target;
    /** 被考核人/班组名称（如 质检员·李检 / 分拣一班·孙姐 / 装卸二班） */
    private String targetName;
    @Enumerated(EnumType.STRING)
    private AssessmentResult result;
    /** 考核扣分（正分，越多越严重；加分/撤销时为 0） */
    private BigDecimal scoreChange = BigDecimal.ZERO;
    /** 考核扣款金额（元，可 0） */
    private BigDecimal penaltyAmount = BigDecimal.ZERO;
    /** 考核事由 */
    private String reason;
    /** 登记人（复核人） */
    private String operator;
}
