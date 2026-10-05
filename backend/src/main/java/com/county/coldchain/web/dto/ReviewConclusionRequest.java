package com.county.coldchain.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 周转筐坏果线索 + 复核结论。
 * 线索把筐号、分拣工位、搬运时间连到一起；结论据此判定
 * 来货质量差（货主承担）/ 装卸挤压 / 分拣台长时间暴露（冷库赔付）。
 */
@Data
public class ReviewConclusionRequest {

    /** OWNER_QUALITY / LOADING_SQUEEZE / SORT_EXPOSURE / MIXED */
    @NotNull
    private String conclusion;
    /** 复核确认的最终损耗重量（公斤，≤ 分拣登记损耗） */
    @NotNull
    private BigDecimal confirmedLossWeight;
    /** 货主承担重量（MIXED 时手工拆分；其他结论由结论自动判定，可空） */
    private BigDecimal ownerBorneWeight;
    /** 冷库承担重量（MIXED 时手工拆分，可空） */
    private BigDecimal warehouseBorneWeight;
    /** 冷库赔付单价（可空，取分拣等级货值均价） */
    private BigDecimal compensatePrice;

    /** 坏果是否已流向客户，需要对客赔付 */
    private Boolean customerCompNeeded;
    /** 受偿客户 ID（可空） */
    private Long customerId;
    /** 对客赔付金额（元） */
    private BigDecimal customerCompAmount;
    /** 对客赔付说明 */
    private String customerCompNote;

    /** 复核人签名 */
    @NotNull
    private String reviewer;
    /** 复核综合意见 */
    private String conclusionNote;

    /** 周转筐线索（可在结论时一并提交） */
    private List<BasketClueDto> baskets;

    @Data
    public static class BasketClueDto {
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
        /** ARRIVAL_QUALITY / LOADING_SQUEEZE / SORT_EXPOSURE / INCLUSIVE（可空，按暴露时长建议） */
        private String cause;
        private String causeBasis;
    }
}
