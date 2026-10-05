package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import com.county.coldchain.domain.DomainEnums.LossCause;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 周转筐证据：复核中发现坏果集中时，把筐号、分拣工位、搬运时间连到一起。
 * 每条证据对应一个周转筐，引用其装卸/搬运时间线（{@link CrateHandlingRecord}），
 * 由复核员结合坏果形态判定属于来货质量差、装卸挤压还是分拣台长时间暴露。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_review_crate_evidence")
public class CrateEvidence extends BaseEntity {
    private Long reviewId;
    private Long batchId;
    private String crateNo;
    /** 该筐坏果重量（公斤） */
    private BigDecimal badFruitWeight = BigDecimal.ZERO;
    /** 关联分拣工位 */
    private String sortStation;
    /** 关联搬运记录 ID（CrateHandlingRecord） */
    private Long handlingId;
    /** 到货时间（来自搬运链） */
    private LocalDateTime arrivedAt;
    /** 卸车/搬运到分拣台时间 */
    private LocalDateTime unloadedAt;
    /** 到达分拣台时间 */
    private LocalDateTime reachedStationAt;
    /** 分拣台暴露时长（分钟，= 开拣 - 到台） */
    private Integer exposedMinutes;
    /** 筐体挤压痕迹/坏果形态描述 */
    @Column(columnDefinition = "text")
    private String damagePattern;
    @Enumerated(EnumType.STRING)
    private LossCause indicatedCause;
    @Column(columnDefinition = "text")
    private String remark;
}
