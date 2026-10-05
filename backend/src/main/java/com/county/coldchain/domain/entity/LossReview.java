package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.LossCause;
import com.county.coldchain.domain.DomainEnums.Responsibility;
import com.county.coldchain.domain.DomainEnums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分拣损耗争议复核单：货主不认可分拣损耗比例后由复核人员发起。
 * 平台向货主展示原始入库重量、分拣视频、坏果照片、等级划分与质检员签名；
 * 复核员可重新抽样并调整可售重量，最终认定损耗成因与责任：
 * 到货质量 → 货主承担；分拣操作不当（装卸挤压/分拣台暴露）→ 冷库赔付。
 * 结案后回写分拣单、结算单口径、质检员考核，并生成客户赔付记录。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_loss_review", indexes = {
        @Index(name = "idx_review_no", columnList = "reviewNo", unique = true),
        @Index(name = "idx_review_batch", columnList = "batchId"),
        @Index(name = "idx_review_status", columnList = "status")
})
public class LossReview extends BaseEntity {
    private String reviewNo;
    private Long batchId;
    private Long sortingId;
    private Long ownerId;

    /** 争议货主主张的损耗率上限（0-1，可空） */
    private BigDecimal ownerClaimLossRate;
    @Column(columnDefinition = "text")
    private String ownerClaimNote;

    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.OPEN;

    /** 复核人员 */
    private String reviewer;
    private LocalDateTime startedAt;
    private LocalDateTime sampledAt;
    private LocalDateTime concludedAt;

    // ---- 重新抽样复核口径 ----
    /** 复核抽样总重量（公斤） */
    private BigDecimal sampleWeight = BigDecimal.ZERO;
    /** 抽样中坏果重量（公斤） */
    private BigDecimal sampleRotWeight = BigDecimal.ZERO;
    /** 抽样坏果率（0-1） */
    private BigDecimal sampleRotRate = BigDecimal.ZERO;
    /** 按抽样结果推算的全量复核损耗（公斤） */
    private BigDecimal reviewedLossWeight;
    /** 复核调整后的可售重量（公斤） */
    private BigDecimal reviewedSaleableWeight;
    /** 冷库应承担赔付的损耗重量（公斤，= 原登记损耗 - 复核认定货主承担损耗） */
    private BigDecimal warehouseBorneWeight = BigDecimal.ZERO;

    // ---- 结论 ----
    @Enumerated(EnumType.STRING)
    private LossCause lossCause;
    @Enumerated(EnumType.STRING)
    private Responsibility responsibility;
    /** 冷库赔付货主金额（元，按分拣均价） */
    private BigDecimal compensateAmount = BigDecimal.ZERO;
    /** 是否发现坏果集中在某几个周转筐 */
    private Boolean crateConcentrated = false;
    /** 证据链分析：筐号 + 分拣工位 + 搬运时间串联结论 */
    @Column(columnDefinition = "text")
    private String crateAnalysis;
    /** 复核结论说明 */
    @Column(columnDefinition = "text")
    private String conclusion;
    /** 复核人签名（文本/签名编号） */
    private String reviewerSignature;

    // ---- 回写状态 ----
    /** 是否已回写分拣单与批次重量口径 */
    private Boolean writtenBack = false;
    /** 是否已回写质检员考核 */
    private Boolean performanceWritten = false;
    /** 被考核质检员姓名（取质检单签名） */
    private String inspectorName;
    /** 本次考核扣分（正整数，来货质量漏检漏判时产生） */
    private Integer inspectorDeductedPoints = 0;
    /** 关联客户赔付记录 ID（冷库对客赔付时） */
    private Long customerCompensationId;
}
