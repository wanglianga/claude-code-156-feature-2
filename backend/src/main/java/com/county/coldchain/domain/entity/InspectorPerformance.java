package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质检员考核流水：损耗争议复核结案后回写。
 * 来货质量差但质检未识别（腐损率漏判）→ 扣分；复核维持质检判断 → 不扣分。
 * 按质检员姓名汇总形成分拣班组/质检员考核口径。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_inspector_performance", indexes = {
        @Index(name = "idx_perf_inspector", columnList = "inspector"),
        @Index(name = "idx_perf_batch", columnList = "batchId")
})
public class InspectorPerformance extends BaseEntity {
    private String inspector;
    private Long batchId;
    private Long reviewId;
    private String reviewNo;
    /** 关联质检记录 ID */
    private Long inspectionId;
    /** 考核事项标题 */
    private String title;
    /** 本次扣分（0 表示无责/维持原判） */
    private Integer deductedPoints = 0;
    /** 加扣分后累计分值（冗余，便于列表直接展示） */
    private Integer scoreAfter;
    /** 是否质检漏判（来货质量问题未在验收时识别） */
    private Boolean missJudged = false;
    @Column(columnDefinition = "text")
    private String detail;
    private LocalDateTime occurredAt;
}
