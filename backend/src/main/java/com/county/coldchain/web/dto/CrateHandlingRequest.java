package com.county.coldchain.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 周转筐装卸/搬运时间线录入（到货、卸车、到分拣台、开拣）。 */
@Data
public class CrateHandlingRequest {
    private Long batchId;
    private Long sortingId;
    private String crateNo;
    private Integer stackLayer;
    private Integer stackHeight;
    private String vehicleNo;
    private String sortStation;
    private LocalDateTime arrivedAt;
    private LocalDateTime unloadedAt;
    private LocalDateTime reachedStationAt;
    private LocalDateTime sortingStartedAt;
    private String handler;
    private String remark;
}
