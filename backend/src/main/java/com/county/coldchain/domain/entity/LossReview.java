package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.ReviewConclusion;
import com.county.coldchain.domain.DomainEnums.ReviewStatus;
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
 * 分拣损耗争议复核单：货主认为分拣后损耗比例过高时发起。
 * 平台向复核人员展示原始入库重量、分拣视频、坏果照片、等级划分与质检员签名（证据快照）；
 * 复核人员可重新抽样并调整可售重量。
 * 损耗来自到货质量 → 货主承担；来自装卸挤压/分拣操作不当 → 冷库赔付。
 * 结论回写结算单（账单）与质检员/班组考核，并生成客户赔付记录。
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
    /** 争议对应的分拣作业单 */
    private Long sortingId;
    /** 发起争议的货主 */
    private Long ownerId;
    /** 申请人（货主/登记人） */
    private String applicant;
    private LocalDateTime appliedAt;
    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.OPEN;

    /** 货主主张的合理损耗率（0-1） */
    private BigDecimal claimedLossRate;
    /** 货主争议说明 */
    private String claimReason;

    // ---- 平台向复核人员展示的证据快照 ----
    /** 原始入库净重（公斤，取自到货批次） */
    private BigDecimal originalNetWeight;
    /** 分拣投入重量快照 */
    private BigDecimal sortingInputWeight;
    /** 分拣登记产出重量快照 */
    private BigDecimal sortingOutputWeight;
    /** 分拣登记损耗重量快照 */
    private BigDecimal sortingLossWeight;
    /** 分拣损耗率快照（0-1） */
    private BigDecimal sortingLossRate;
    /** 分拣视频（编号/文件名/URL，演示用文本） */
    private String sortingVideo;
    /** 坏果照片（编号/文件名/URL 列表，演示用换行/逗号文本） */
    private String badFruitPhotos;
    /** 等级划分说明（A/B/加工 各重量与售价快照） */
    private String gradeBreakdown;
    /** 质检员签名（质检验收人） */
    private String qcSignature;
    /** 质检员对证据的说明 */
    private String qcEvidenceNote;

    // ---- 重新抽样 ----
    /** 抽样总重量（公斤） */
    private BigDecimal sampleWeight = BigDecimal.ZERO;
    /** 抽样中坏果/损耗重量（公斤） */
    private BigDecimal sampleBadWeight = BigDecimal.ZERO;
    /** 抽样坏果率（0-1） */
    private BigDecimal sampleBadRate = BigDecimal.ZERO;
    /** 抽样复核人 */
    private String sampler;
    private LocalDateTime sampledAt;
    /** 抽样发现说明 */
    private String sampleNote;

    // ---- 复核结论 ----
    @Enumerated(EnumType.STRING)
    private ReviewConclusion conclusion;
    /** 复核确认最终损耗重量（公斤） */
    private BigDecimal confirmedLossWeight;
    /** 货主承担损耗（到货质量）重量 */
    private BigDecimal ownerBorneWeight = BigDecimal.ZERO;
    /** 冷库赔付/承担损耗（装卸挤压+分拣暴露）重量 */
    private BigDecimal warehouseBorneWeight = BigDecimal.ZERO;
    /** 复核后可售重量调整：回补为可售的重量（公斤） */
    private BigDecimal salableAdjustWeight = BigDecimal.ZERO;
    /** 冷库赔付单价（元/公斤，缺省取分拣货位均价） */
    private BigDecimal compensatePrice;
    /** 冷库赔付金额（元） */
    private BigDecimal compensateAmount = BigDecimal.ZERO;
    /** 是否需要对客户赔付（坏果已流出） */
    private Boolean customerCompNeeded = false;
    /** 对客赔付金额（元） */
    private BigDecimal customerCompAmount = BigDecimal.ZERO;
    /** 对客赔付说明（流向客户） */
    private String customerCompNote;
    /** 复核人签名 */
    private String reviewer;
    private LocalDateTime confirmedAt;
    /** 复核综合意见 */
    private String conclusionNote;

    /** 关联已回写的账单（结论回写结算单后记录） */
    private Long billId;
}
