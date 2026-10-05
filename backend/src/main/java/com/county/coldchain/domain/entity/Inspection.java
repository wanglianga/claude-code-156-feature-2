package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.Decision;
import com.county.coldchain.domain.DomainEnums.Grade;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质检验收记录：外观、成熟度、腐损、含水、温度、票证。
 * 结论：入库 / 降级 / 退货 / 分拣。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_inspection")
public class Inspection extends BaseEntity {
    private Long batchId;
    private String inspector;
    private LocalDateTime inspectedAt;

    /** 外观评分 0-100 */
    private Integer appearanceScore;
    /** 成熟度评分 0-100 */
    private Integer maturityScore;
    /** 实测腐损率（0-1） */
    private BigDecimal rotRate;
    /** 含水率（0-1） */
    private BigDecimal moistureRate;
    /** 实测到货温度（℃） */
    private BigDecimal measuredTemp;
    /** 票证是否齐全/真实 */
    private Boolean certificateOk;
    /** 是否混批混装确认 */
    private Boolean mixedConfirmed;
    /** 建议等级（直接入库/降级时使用） */
    @Enumerated(EnumType.STRING)
    private Grade suggestedGrade;
    @Enumerated(EnumType.STRING)
    private Decision decision;
    /** 降级/退货重量（公斤） */
    private BigDecimal downgradeWeight = BigDecimal.ZERO;
    private BigDecimal returnWeight = BigDecimal.ZERO;
    /** 拟报损率（0-1，直接入库时） */
    private BigDecimal estimatedLossRate = BigDecimal.ZERO;
    /** 质检员电子签名（签名图片 URL/编号或手写签名文本），争议复核平台证据之一 */
    private String signature;
    private String remark;
}
