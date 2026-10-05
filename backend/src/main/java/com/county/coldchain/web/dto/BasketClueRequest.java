package com.county.coldchain.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 复核过程中登记的周转筐坏果线索（筐号 + 分拣工位 + 搬运时间串联）。 */
@Data
public class BasketClueRequest {
    @NotNull
    private String basketNo;
    private String workstation;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sortStationAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime movedAwayAt;
    private String mover;
    /** 到货卸车 / 上台分拣 / 入库搬运 / 装车出库 */
    private String moveStage;
    private BigDecimal badRate;
    /** ARRIVAL_QUALITY / LOADING_SQUEEZE / SORT_EXPOSURE / INCLUSIVE（可空，按暴露时长自动建议） */
    private String cause;
    private String causeBasis;
}
