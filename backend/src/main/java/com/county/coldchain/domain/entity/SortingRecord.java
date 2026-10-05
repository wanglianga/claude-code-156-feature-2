package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分拣记录：一次分拣作业的主单。
 * 分拣工时（低温仓位作业）通过 laborMinutes 进入账单。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_sorting")
public class SortingRecord extends BaseEntity {
    private String sortingNo;
    private Long batchId;
    private String sorter;
    private LocalDateTime sortedAt;
    /** 分拣前投入重量（公斤） */
    private BigDecimal inputWeight;
    /** 分拣后各等级可售合计（公斤，由明细汇总） */
    private BigDecimal outputWeight = BigDecimal.ZERO;
    /** 分拣损耗重量（公斤，= input - output） */
    private BigDecimal lossWeight = BigDecimal.ZERO;
    /** 分拣损耗率（0-1） */
    private BigDecimal lossRate = BigDecimal.ZERO;
    /** 分拣工时（分钟） */
    private Integer laborMinutes = 0;
    /** 货主对损耗有争议 */
    private Boolean disputed = false;
    /** 争议说明 / 裁定备注 */
    @Column(columnDefinition = "text")
    private String disputeNote;
    /** 争议是否已裁定（旧流程直接裁定；新流程由争议复核单结案回写） */
    private Boolean disputeResolved = false;
    /** 裁定最终损耗重量（冷库责任差额 = lossWeight - adjudicatedLossWeight） */
    private BigDecimal adjudicatedLossWeight;
    /** 分拣视频 URL/编号（争议复核平台证据） */
    private String sortVideoUrl;
    /** 坏果照片 URL/编号，多个以逗号分隔（争议复核平台证据） */
    @Column(columnDefinition = "text")
    private String badFruitPhotos;
    /** 分拣工位（例如 1号分拣台，用于周转筐证据链关联） */
    private String sortStation;
    /** 关联的争议复核单（结案后回写） */
    private Long reviewId;
    private String remark;
}
