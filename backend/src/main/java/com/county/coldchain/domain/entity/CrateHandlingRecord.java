package com.county.coldchain.domain.entity;

import com.county.coldchain.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 周转筐装卸/搬运时间线：每个周转筐从到货、卸车、到分拣台、开拣的时间点与工位。
 * 争议复核时作为"筐号 ↔ 分拣工位 ↔ 搬运时间"证据链的原始数据，
 * 用于识别装卸挤压（底层筐/堆叠超高）与分拣台长时间暴露（到台后久未开拣）。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_crate_handling")
public class CrateHandlingRecord extends BaseEntity {
    private Long batchId;
    private Long sortingId;
    private String crateNo;
    /** 装车序号/堆层（底层筐更易被挤压） */
    private Integer stackLayer;
    /** 堆叠层数（超高提示挤压风险） */
    private Integer stackHeight;
    private String vehicleNo;
    private String sortStation;
    private LocalDateTime arrivedAt;
    private LocalDateTime unloadedAt;
    private LocalDateTime reachedStationAt;
    private LocalDateTime sortingStartedAt;
    private String handler;
    @Column(columnDefinition = "text")
    private String remark;
}
